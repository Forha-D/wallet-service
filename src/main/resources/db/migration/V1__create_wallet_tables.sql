-- V1__create_wallet_tables.sql

-- ========================
-- WALLETS
-- ========================
CREATE TABLE wallets (
    id          UUID                     DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id     UUID                     NOT NULL UNIQUE,
    balance     NUMERIC(19, 4)           NOT NULL DEFAULT 0.0000,
    currency    VARCHAR(3)               NOT NULL DEFAULT 'BDT',
    status      VARCHAR(20)              NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT balance_non_negative CHECK (balance >= 0),
    CONSTRAINT valid_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT valid_currency CHECK (currency IN ('BDT', 'USD'))
);

-- ========================
-- WALLET TRANSACTIONS
-- ========================
CREATE TABLE wallet_transactions (
    id              UUID                     DEFAULT gen_random_uuid() PRIMARY KEY,
    wallet_id       UUID                     NOT NULL REFERENCES wallets(id),
    type            VARCHAR(10)              NOT NULL,
    amount          NUMERIC(19, 4)           NOT NULL,
    balance_before  NUMERIC(19, 4)           NOT NULL,
    balance_after   NUMERIC(19, 4)           NOT NULL,
    idempotency_key VARCHAR(255)             NOT NULL UNIQUE,
    reference_id    UUID,
    reference_type  VARCHAR(50),
    description     TEXT,
    metadata        JSONB,
    status          VARCHAR(20)              NOT NULL DEFAULT 'COMPLETED',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT positive_amount CHECK (amount > 0),
    CONSTRAINT valid_type CHECK (type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT valid_tx_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED'))
);

-- ========================
-- WALLET OUTBOX
-- ========================
CREATE TABLE wallet_outbox (
    id           UUID                     DEFAULT gen_random_uuid() PRIMARY KEY,
    event_type   VARCHAR(100)             NOT NULL,
    payload      JSONB                    NOT NULL,
    status       VARCHAR(20)              NOT NULL DEFAULT 'PENDING',
    wallet_id    UUID                     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT valid_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- ========================
-- INDEXES
-- ========================

-- wallets
CREATE INDEX idx_wallet_user_id
    ON wallets(user_id);

-- wallet_transactions
CREATE INDEX idx_tx_wallet_id
    ON wallet_transactions(wallet_id);

CREATE INDEX idx_tx_wallet_created
    ON wallet_transactions(wallet_id, created_at DESC);

CREATE INDEX idx_tx_idempotency_key
    ON wallet_transactions(idempotency_key);

CREATE INDEX idx_tx_reference
    ON wallet_transactions(reference_id, reference_type);

-- wallet_outbox
CREATE INDEX idx_outbox_status
    ON wallet_outbox(status)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_wallet_id
    ON wallet_outbox(wallet_id);