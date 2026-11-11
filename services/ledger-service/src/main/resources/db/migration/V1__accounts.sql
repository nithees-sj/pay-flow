CREATE TABLE ledger_accounts (
 id UUID PRIMARY KEY,owner_user_id UUID,kind VARCHAR(20) NOT NULL CHECK(kind IN('USER','SYSTEM_FUNDING')),
 currency CHAR(3) NOT NULL CHECK(currency='INR'),balance_minor BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),UNIQUE(id,currency),
 CHECK((kind='USER' AND owner_user_id IS NOT NULL AND balance_minor>=0) OR(kind='SYSTEM_FUNDING' AND owner_user_id IS NULL)));
CREATE UNIQUE INDEX user_wallet_unique ON ledger_accounts(owner_user_id,currency) WHERE kind='USER';
CREATE UNIQUE INDEX system_funding_unique ON ledger_accounts(currency) WHERE kind='SYSTEM_FUNDING';
INSERT INTO ledger_accounts(id,kind,currency) VALUES('00000000-0000-0000-0000-000000000001','SYSTEM_FUNDING','INR');
