CREATE TABLE transactions (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT         NOT NULL REFERENCES users (id),
    category_id      BIGINT         NOT NULL REFERENCES categories (id),
    title            VARCHAR(255)   NOT NULL,
    amount           NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)     NOT NULL DEFAULT 'USD',
    transaction_date DATE           NOT NULL,
    notes            TEXT,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX transactions_user_id_idx ON transactions (user_id);
CREATE INDEX transactions_user_date_idx ON transactions (user_id, transaction_date);
