# Data model

Four business databases: accounts_db, ledger_db, payments_db, notification_db. A reserved gateway_db and role satisfy infrastructure isolation without gateway business tables. All business IDs are UUID; timestamps are TIMESTAMPTZ. Unless stated nullable, columns are NOT NULL. created_at defaults to now(); counters default to zero. Foreign keys exist only within the owning database. Flyway owns flyway_schema_history in each stateful database (installed_rank INTEGER PK, version VARCHAR(50) nullable, description VARCHAR(200), type VARCHAR(20), script VARCHAR(1000), checksum INTEGER nullable, installed_by VARCHAR(100), installed_on TIMESTAMP DEFAULT now(), execution_time INTEGER, success BOOLEAN; success indexed). Hibernate ddl-auto=validate, never update.

## Accounts

- **users**: id UUID PK; email VARCHAR(254) UNIQUE canonical lowercase/trimmed; name VARCHAR(100); password_hash VARCHAR(100); created_at, updated_at TIMESTAMPTZ.
- **wallets**: id UUID PK (same as ledger account ID); user_id UUID FK users(id); currency CHAR(3) CHECK INR; status VARCHAR(20) CHECK PROVISIONING/ACTIVE; provisioning_attempts INT DEFAULT 0; next_attempt_at TIMESTAMPTZ DEFAULT now(); lease_until TIMESTAMPTZ nullable; last_error_code VARCHAR(64) nullable; created_at, updated_at. UNIQUE(user_id,currency). Partial due-work index (next_attempt_at) WHERE status='PROVISIONING'.

## Ledger

- **ledger_accounts**: id UUID PK; owner_user_id UUID nullable; kind VARCHAR(20) CHECK USER/SYSTEM_FUNDING; currency CHAR(3) CHECK INR; balance_minor BIGINT DEFAULT 0; created_at, updated_at. CHECK USER requires owner and balance>=0; SYSTEM_FUNDING requires no owner. UNIQUE(id,currency); partial UNIQUE(owner_user_id,currency) for USER; partial UNIQUE(currency) for SYSTEM_FUNDING.
- **transfers**: transfer_id UUID PK; from_account_id UUID FK ledger_accounts; to_account_id UUID FK ledger_accounts; amount_minor BIGINT CHECK >0; currency CHAR(3) CHECK INR; request_hash CHAR(64); status VARCHAR(16) CHECK POSTED/REJECTED; failure_code VARCHAR(64) nullable; created_at. CHECK from!=to. POSTED has no failure; REJECTED has failure. Outcomes immutable.
- **ledger_entries**: id UUID PK; account_id UUID; transfer_id UUID FK transfers; direction VARCHAR(6) CHECK DEBIT/CREDIT; amount_minor BIGINT CHECK >0; currency CHAR(3); created_at. Composite FK(account_id,currency) -> ledger_accounts(id,currency); UNIQUE(transfer_id,direction). Index(account_id,created_at DESC,id DESC). Updates/deletes forbidden by trigger.

Deferred constraint triggers on transfers and entries check at commit: POSTED has exactly two matching entries, one source DEBIT and one destination CREDIT, equal to transfer amount/currency; REJECTED has zero entries. Cached balances change in the same transaction and invariant tests compare SUM(CREDIT-DEBIT). Transfer identity concurrency is serialized by transaction advisory lock, then row locks are acquired ORDER BY id FOR UPDATE. Advisory hash collisions only reduce concurrency, not correctness.

## Payments

- **payments**: id UUID PK; transfer_id UUID UNIQUE; kind VARCHAR(16) CHECK PAYMENT/TEST_TOPUP; initiated_by UUID; sender_user_id UUID nullable; recipient_user_id UUID nullable; recipient_email VARCHAR(254) nullable; from_account_id, to_account_id UUID nullable; amount_minor BIGINT; currency CHAR(3) CHECK INR; note VARCHAR(280) nullable; status VARCHAR(16) CHECK CREATED/COMPLETED/FAILED/DECLINED; reason_code VARCHAR(64) nullable; risk_decision VARCHAR(64) nullable; created_at, updated_at; completed_at TIMESTAMPTZ nullable; reconcile_attempts INT DEFAULT 0; next_reconcile_at TIMESTAMPTZ DEFAULT now(); lease_until TIMESTAMPTZ nullable; version BIGINT DEFAULT 1; trace_id VARCHAR(64). Negative/zero requested amounts may be recorded only as CREATED awaiting risk or DECLINED. Non-CREATED outcomes immutable. Indexes(initiated_by,created_at DESC,id DESC), (recipient_user_id,created_at DESC,id DESC) WHERE status='COMPLETED', (next_reconcile_at) WHERE status='CREATED'.
- **payment_transitions**: id UUID PK; payment_id UUID FK payments; from_status VARCHAR(16) nullable for initial transition; to_status VARCHAR(16); reason_code VARCHAR(64) nullable; occurred_at TIMESTAMPTZ. UNIQUE(payment_id,to_status); partial UNIQUE(payment_id) WHERE to_status<>'CREATED'. Only NULL->CREATED and CREATED->terminal allowed.
- **idempotency_records**: user_id UUID; operation VARCHAR(16) CHECK PAYMENT/TEST_TOPUP; key_hash CHAR(64); request_hash CHAR(64); payment_id UUID UNIQUE FK payments; response_status INT nullable; response_body JSONB nullable; response_headers JSONB nullable; created_at, expires_at TIMESTAMPTZ. PK(user_id,operation,key_hash); index(expires_at). Response status/body must either both be null or both populated. Expiry equals accepted time+24h. Replacement and old-worker response writes are guarded by payment_id.
- **risk_attempts**: payment_id UUID PK FK payments; user_id UUID; amount_minor BIGINT CHECK >0; currency CHAR(3) CHECK INR; attempted_at TIMESTAMPTZ. Index(user_id,attempted_at). The attempt row and risk decision commit together after Redis evaluation, under the per-user advisory lock. Redis failure rolls back the attempt and defers processing. Committed attempts conservatively seed reconstruction; Redis increments surviving a database rollback may conservatively consume capacity.
- **outbox_events**: event_id UUID PK; payment_id UUID UNIQUE FK payments; event_type VARCHAR(32); schema_version INT CHECK =1; partition_key VARCHAR(36); payload JSONB; created_at; published_at TIMESTAMPTZ nullable; attempts INT DEFAULT 0; next_attempt_at TIMESTAMPTZ DEFAULT now(); lease_owner UUID nullable; lease_until TIMESTAMPTZ nullable; last_error_code VARCHAR(64) nullable. Partial index(next_attempt_at,created_at) WHERE published_at IS NULL.

## Notifications

- **consumed_events**: event_id UUID PK; event_type VARCHAR(32); schema_version INT; processed_at TIMESTAMPTZ DEFAULT now(). Retain receipts as long as events can be replayed; no automatic deletion in V1.
- **notifications**: id UUID PK; event_id UUID FK consumed_events; user_id UUID; payment_id UUID; type VARCHAR(32); message_key VARCHAR(64); message_parameters JSONB; created_at; read_at TIMESTAMPTZ nullable. UNIQUE(event_id,user_id); index(user_id,created_at DESC,id DESC); partial index(user_id) WHERE read_at IS NULL.

## Redis and Kafka

Redis key namespaces: idem:<user>:<operation>:<sha256-key> (stored response, absolute 24h expiry); risk:<user>:<UTC-minute/day> (bounded counters); gateway:<identity>:<window> (rate limit). Redis is not the final authority for payment uniqueness. No secret/raw idempotency key appears in a key name. Kafka partitions by payment UUID. payments.events and payments.events.DLT are local single-broker topics; event receipts prevent duplicate database side effects.

## Money and time

Amounts/entry values are BIGINT. Java arithmetic is checked. Journal aggregate queries cast to NUMERIC before summing to avoid aggregate overflow. INR has 2 minor digits. Wire amounts use decimal strings including negative balances for funding. Time windows use UTC. System funding can be negative; customer wallets cannot.
