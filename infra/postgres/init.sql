\getenv gateway_password GATEWAY_DB_PASSWORD
CREATE ROLE gateway LOGIN PASSWORD :'gateway_password';
CREATE DATABASE gateway_db OWNER gateway;
REVOKE ALL ON DATABASE gateway_db FROM PUBLIC;
GRANT CONNECT ON DATABASE gateway_db TO gateway;
\getenv accounts_password ACCOUNTS_DB_PASSWORD
CREATE ROLE accounts LOGIN PASSWORD :'accounts_password';
CREATE DATABASE accounts_db OWNER accounts;
REVOKE ALL ON DATABASE accounts_db FROM PUBLIC;
GRANT CONNECT ON DATABASE accounts_db TO accounts;
\getenv ledger_password LEDGER_DB_PASSWORD
CREATE ROLE ledger LOGIN PASSWORD :'ledger_password';
CREATE DATABASE ledger_db OWNER ledger;
REVOKE ALL ON DATABASE ledger_db FROM PUBLIC;
GRANT CONNECT ON DATABASE ledger_db TO ledger;
\getenv payments_password PAYMENTS_DB_PASSWORD
CREATE ROLE payments LOGIN PASSWORD :'payments_password';
CREATE DATABASE payments_db OWNER payments;
REVOKE ALL ON DATABASE payments_db FROM PUBLIC;
GRANT CONNECT ON DATABASE payments_db TO payments;
\getenv notification_password NOTIFICATION_DB_PASSWORD
CREATE ROLE notification LOGIN PASSWORD :'notification_password';
CREATE DATABASE notification_db OWNER notification;
REVOKE ALL ON DATABASE notification_db FROM PUBLIC;
GRANT CONNECT ON DATABASE notification_db TO notification;
