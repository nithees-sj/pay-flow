CREATE TABLE payments (
 id UUID PRIMARY KEY, transfer_id UUID NOT NULL UNIQUE,kind VARCHAR(16) NOT NULL CHECK(kind IN('PAYMENT','TEST_TOPUP')),
 initiated_by UUID NOT NULL,sender_user_id UUID,recipient_user_id UUID,recipient_email VARCHAR(254),from_account_id UUID,to_account_id UUID,
 amount_minor BIGINT NOT NULL,currency CHAR(3) NOT NULL CHECK(currency='INR'),note VARCHAR(280),
 status VARCHAR(16) NOT NULL CHECK(status IN('CREATED','COMPLETED','FAILED','DECLINED')),reason_code VARCHAR(64),risk_decision VARCHAR(64),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),completed_at TIMESTAMPTZ,
 reconcile_attempts INT NOT NULL DEFAULT 0,next_reconcile_at TIMESTAMPTZ NOT NULL DEFAULT now(),lease_until TIMESTAMPTZ,
 version BIGINT NOT NULL DEFAULT 1,trace_id VARCHAR(64) NOT NULL,
 CHECK(amount_minor>0 OR status IN('CREATED','DECLINED')));
CREATE INDEX payments_sender_history ON payments(initiated_by,created_at DESC,id DESC);
CREATE INDEX payments_receiver_history ON payments(recipient_user_id,created_at DESC,id DESC) WHERE status='COMPLETED';
CREATE INDEX payments_due ON payments(next_reconcile_at) WHERE status='CREATED';
CREATE TABLE payment_transitions (
 id UUID PRIMARY KEY,payment_id UUID NOT NULL REFERENCES payments(id),from_status VARCHAR(16),to_status VARCHAR(16) NOT NULL,reason_code VARCHAR(64),occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(payment_id,to_status),CHECK((from_status IS NULL AND to_status='CREATED') OR(from_status='CREATED' AND to_status IN('COMPLETED','FAILED','DECLINED'))));
CREATE UNIQUE INDEX one_terminal_transition ON payment_transitions(payment_id) WHERE to_status<>'CREATED';
CREATE TABLE idempotency_records (
 user_id UUID NOT NULL,operation VARCHAR(16) NOT NULL CHECK(operation IN('PAYMENT','TEST_TOPUP')),key_hash CHAR(64) NOT NULL,request_hash CHAR(64) NOT NULL,
 payment_id UUID NOT NULL UNIQUE REFERENCES payments(id),response_status INT,response_body JSONB,response_headers JSONB,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),expires_at TIMESTAMPTZ NOT NULL DEFAULT now()+interval '24 hours',
 PRIMARY KEY(user_id,operation,key_hash),CHECK((response_status IS NULL)=(response_body IS NULL)));
CREATE INDEX idempotency_expiry ON idempotency_records(expires_at);
CREATE TABLE risk_attempts (payment_id UUID PRIMARY KEY REFERENCES payments(id),user_id UUID NOT NULL,amount_minor BIGINT NOT NULL CHECK(amount_minor>0),currency CHAR(3) NOT NULL CHECK(currency='INR'),attempted_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX risk_window ON risk_attempts(user_id,attempted_at);
CREATE TABLE outbox_events (
 event_id UUID PRIMARY KEY,payment_id UUID NOT NULL UNIQUE REFERENCES payments(id),event_type VARCHAR(32) NOT NULL,schema_version INT NOT NULL CHECK(schema_version=1),partition_key VARCHAR(36) NOT NULL,payload JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),published_at TIMESTAMPTZ,attempts INT NOT NULL DEFAULT 0,next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),lease_owner UUID,lease_until TIMESTAMPTZ,last_error_code VARCHAR(64));
CREATE INDEX outbox_due ON outbox_events(next_attempt_at,created_at) WHERE published_at IS NULL;
CREATE FUNCTION payment_state_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.status<>'CREATED' THEN RAISE EXCEPTION 'Terminal payment is immutable' USING ERRCODE='23514'; END IF;
 IF NEW.status NOT IN('CREATED','COMPLETED','FAILED','DECLINED') THEN RAISE EXCEPTION 'Illegal transition' USING ERRCODE='23514'; END IF;
 IF NEW.transfer_id<>OLD.transfer_id OR NEW.initiated_by<>OLD.initiated_by OR NEW.amount_minor<>OLD.amount_minor OR NEW.currency<>OLD.currency OR NEW.kind<>OLD.kind THEN RAISE EXCEPTION 'Payment intent is immutable' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER payment_state BEFORE UPDATE ON payments FOR EACH ROW EXECUTE FUNCTION payment_state_guard();
