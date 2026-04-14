-- V1__create_transactions_schema.sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE transactions (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reference_number    VARCHAR(50)  UNIQUE NOT NULL,
    account_id          VARCHAR(30)  NOT NULL,
    counterparty_account VARCHAR(30),
    type                VARCHAR(20)  NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    amount              NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    currency            CHAR(3)      NOT NULL DEFAULT 'ZAR',
    region              VARCHAR(20)  NOT NULL,
    description         TEXT,
    failure_reason      TEXT,
    version             BIGINT       DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_txn_account    ON transactions(account_id);
CREATE INDEX idx_txn_status     ON transactions(status);
CREATE INDEX idx_txn_reference  ON transactions(reference_number);
CREATE INDEX idx_txn_created_at ON transactions(created_at DESC);
CREATE INDEX idx_txn_region     ON transactions(region);
CREATE INDEX idx_txn_region_status ON transactions(region, status);

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$ BEGIN NEW.updated_at = NOW(); RETURN NEW; END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
