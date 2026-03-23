CREATE TABLE transactions (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES users(id),
    category_id      BIGINT NOT NULL REFERENCES categories(id),
    title            VARCHAR(255) NOT NULL,
    amount           DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3) NOT NULL DEFAULT 'USD',
    transaction_date DATE NOT NULL,
    notes            TEXT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
