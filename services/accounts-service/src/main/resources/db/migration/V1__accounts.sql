CREATE TABLE users (
 id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE CHECK(email=lower(btrim(email))),
 name VARCHAR(100) NOT NULL, password_hash VARCHAR(100) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE wallets (
 id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id),currency CHAR(3) NOT NULL CHECK(currency='INR'),
 status VARCHAR(20) NOT NULL CHECK(status IN('PROVISIONING','ACTIVE')),provisioning_attempts INT NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),lease_until TIMESTAMPTZ,last_error_code VARCHAR(64),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),UNIQUE(user_id,currency));
CREATE INDEX wallets_due ON wallets(next_attempt_at) WHERE status='PROVISIONING';
