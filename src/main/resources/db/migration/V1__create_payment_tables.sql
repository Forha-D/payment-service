-- V1__create_payment_tables.sql

-- ========================
-- PAYMENTS
-- ========================
CREATE TABLE payments (
    id              UUID                     DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id         UUID                     NOT NULL,
    amount          NUMERIC(19, 4)           NOT NULL,
    currency        VARCHAR(3)               NOT NULL DEFAULT 'BDT',
    gateway         VARCHAR(20)              NOT NULL,
    type            VARCHAR(20)              NOT NULL DEFAULT 'TOPUP',
    status          VARCHAR(20)              NOT NULL DEFAULT 'INITIATED',
    idempotency_key VARCHAR(255)             NOT NULL UNIQUE,
    gateway_ref     VARCHAR(255),
    payment_url     VARCHAR(1000),
    gateway_token   VARCHAR(500),
    metadata        JSONB,
    failure_reason  TEXT,
    initiated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    confirmed_at    TIMESTAMP WITH TIME ZONE,
    failed_at       TIMESTAMP WITH TIME ZONE,
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW() + INTERVAL '30 minutes',
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT positive_amount    CHECK (amount > 0),
    CONSTRAINT valid_gateway      CHECK (gateway IN ('NAGAD', 'BKASH', 'CARD', 'STRIPE')),
    CONSTRAINT valid_type         CHECK (type IN ('TOPUP')),
    CONSTRAINT valid_status       CHECK (status IN ('INITIATED', 'PENDING', 'SUCCESS', 'FAILED', 'REFUNDED', 'EXPIRED')),
    CONSTRAINT valid_currency     CHECK (currency IN ('BDT', 'USD'))
);

-- ========================
-- WEBHOOK LOGS
-- ========================
CREATE TABLE webhook_logs (
    id            UUID                     DEFAULT gen_random_uuid() PRIMARY KEY,
    payment_id    UUID                     REFERENCES payments(id),
    gateway       VARCHAR(20)              NOT NULL,
    raw_payload   JSONB                    NOT NULL,
    signature     VARCHAR(500),
    verified      BOOLEAN                  NOT NULL DEFAULT FALSE,
    action_taken  VARCHAR(100),
    received_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT valid_webhook_gateway CHECK (gateway IN ('NAGAD', 'BKASH', 'CARD', 'STRIPE'))
);

-- ========================
-- PAYMENT OUTBOX
-- ========================
CREATE TABLE payment_outbox (
    id           UUID                     DEFAULT gen_random_uuid() PRIMARY KEY,
    event_type   VARCHAR(100)             NOT NULL,
    payload      JSONB                    NOT NULL,
    status       VARCHAR(20)              NOT NULL DEFAULT 'PENDING',
    payment_id   UUID                     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT valid_outbox_status  CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT valid_event_type     CHECK (event_type IN ('topup.confirmed', 'topup.failed'))
);

-- ========================
-- INDEXES
-- ========================

-- payments
CREATE INDEX idx_payment_user_id
    ON payments(user_id);

CREATE INDEX idx_payment_user_status
    ON payments(user_id, status);

CREATE INDEX idx_payment_gateway_ref
    ON payments(gateway_ref)
    WHERE gateway_ref IS NOT NULL;

CREATE INDEX idx_payment_idempotency
    ON payments(idempotency_key);

CREATE INDEX idx_payment_status_created
    ON payments(status, initiated_at DESC);

-- expired payments cleanup index
CREATE INDEX idx_payment_expires
    ON payments(expires_at)
    WHERE status = 'PENDING';

-- webhook_logs
CREATE INDEX idx_webhook_payment_id
    ON webhook_logs(payment_id);

CREATE INDEX idx_webhook_gateway
    ON webhook_logs(gateway, received_at DESC);

CREATE INDEX idx_webhook_unverified
    ON webhook_logs(verified)
    WHERE verified = FALSE;

-- payment_outbox
CREATE INDEX idx_payment_outbox_status
    ON payment_outbox(status)
    WHERE status = 'PENDING';

CREATE INDEX idx_payment_outbox_payment_id
    ON payment_outbox(payment_id);