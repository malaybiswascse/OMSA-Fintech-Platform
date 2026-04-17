-- V1__create_policies_schema.sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE policies (
    id             UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    policy_number  VARCHAR(40)   UNIQUE NOT NULL,
    customer_id    VARCHAR(30)   NOT NULL,
    type           VARCHAR(20)   NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    premium_amount NUMERIC(12,2) NOT NULL CHECK (premium_amount > 0),
    sum_assured    NUMERIC(15,2) NOT NULL CHECK (sum_assured > 0),
    currency       CHAR(3)       NOT NULL DEFAULT 'ZAR',
    region         VARCHAR(20)   NOT NULL,
    start_date     DATE,
    end_date       DATE,
    version        BIGINT        DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_policy_customer ON policies(customer_id);
CREATE INDEX idx_policy_status   ON policies(status);
CREATE INDEX idx_policy_number   ON policies(policy_number);
CREATE INDEX idx_policy_region   ON policies(region);

CREATE OR REPLACE FUNCTION update_policy_updated_at()
RETURNS TRIGGER AS $$ BEGIN NEW.updated_at = NOW(); RETURN NEW; END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_policies_updated_at
    BEFORE UPDATE ON policies
    FOR EACH ROW EXECUTE FUNCTION update_policy_updated_at();
