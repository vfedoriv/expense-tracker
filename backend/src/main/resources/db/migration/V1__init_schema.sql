CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    provider    VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email       VARCHAR(255),
    display_name VARCHAR(255) NOT NULL,
    avatar_url  VARCHAR(512),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_user_id)
);

CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, name)
);

CREATE TABLE transactions (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT        NOT NULL REFERENCES users(id),
    category_id      BIGINT        NOT NULL REFERENCES categories(id),
    title            VARCHAR(255)  NOT NULL,
    amount           NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)    NOT NULL DEFAULT 'USD',
    transaction_date DATE          NOT NULL,
    notes            TEXT,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_user_date ON transactions(user_id, transaction_date);
CREATE INDEX idx_transactions_user_category ON transactions(user_id, category_id);

CREATE TABLE monthly_budgets (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES users(id),
    year       INT           NOT NULL,
    month      INT           NOT NULL CHECK (month BETWEEN 1 AND 12),
    amount     NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, year, month)
);

CREATE TABLE budget_alert_log (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id),
    year         INT         NOT NULL,
    month        INT         NOT NULL,
    threshold    INT         NOT NULL,
    alerted_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acknowledged BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (user_id, year, month, threshold)
);
