CREATE TABLE transfers (
 transfer_id UUID PRIMARY KEY,from_account_id UUID NOT NULL REFERENCES ledger_accounts(id),
 to_account_id UUID NOT NULL REFERENCES ledger_accounts(id),amount_minor BIGINT NOT NULL CHECK(amount_minor>0),
 currency CHAR(3) NOT NULL CHECK(currency='INR'),request_hash CHAR(64) NOT NULL,
 status VARCHAR(16) NOT NULL CHECK(status IN('POSTED','REJECTED')),failure_code VARCHAR(64),created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK(from_account_id<>to_account_id),CHECK((status='POSTED' AND failure_code IS NULL) OR(status='REJECTED' AND failure_code IS NOT NULL)));
CREATE TABLE ledger_entries (
 id UUID PRIMARY KEY,account_id UUID NOT NULL,transfer_id UUID NOT NULL REFERENCES transfers(transfer_id),
 direction VARCHAR(6) NOT NULL CHECK(direction IN('DEBIT','CREDIT')),amount_minor BIGINT NOT NULL CHECK(amount_minor>0),
 currency CHAR(3) NOT NULL,created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(account_id,currency) REFERENCES ledger_accounts(id,currency),UNIQUE(transfer_id,direction));
CREATE INDEX entries_history ON ledger_entries(account_id,created_at DESC,id DESC);
CREATE FUNCTION immutable_journal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Ledger entries and transfer outcomes are immutable' USING ERRCODE='23514'; END; $$;
CREATE TRIGGER entries_immutable BEFORE UPDATE OR DELETE ON ledger_entries FOR EACH ROW EXECUTE FUNCTION immutable_journal();
CREATE TRIGGER transfers_immutable BEFORE UPDATE OR DELETE ON transfers FOR EACH ROW EXECUTE FUNCTION immutable_journal();
CREATE FUNCTION verify_entry_pair() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE t transfers%ROWTYPE; n INT; correct INT;
BEGIN
 SELECT * INTO t FROM transfers WHERE transfer_id=NEW.transfer_id;
 SELECT count(*),count(*) FILTER(WHERE amount_minor=t.amount_minor AND currency=t.currency AND
 ((direction='DEBIT' AND account_id=t.from_account_id) OR(direction='CREDIT' AND account_id=t.to_account_id)))
 INTO n,correct FROM ledger_entries WHERE transfer_id=NEW.transfer_id;
 IF (t.status='POSTED' AND (n<>2 OR correct<>2)) OR(t.status='REJECTED' AND n<>0) THEN
  RAISE EXCEPTION 'Unbalanced or inconsistent transfer %',NEW.transfer_id USING ERRCODE='23514';
 END IF;
 RETURN NULL;
END; $$;
CREATE CONSTRAINT TRIGGER transfer_pair AFTER INSERT ON transfers DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_entry_pair();
CREATE CONSTRAINT TRIGGER entry_pair AFTER INSERT ON ledger_entries DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION verify_entry_pair();
CREATE FUNCTION immutable_account_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.id<>OLD.id OR NEW.owner_user_id IS DISTINCT FROM OLD.owner_user_id OR NEW.kind<>OLD.kind OR NEW.currency<>OLD.currency THEN
  RAISE EXCEPTION 'Account identity is immutable' USING ERRCODE='23514';
 END IF; RETURN NEW;
END; $$;
CREATE TRIGGER account_identity BEFORE UPDATE ON ledger_accounts FOR EACH ROW EXECUTE FUNCTION immutable_account_identity();
