CREATE TABLE monthly_budgets (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT         NOT NULL REFERENCES users (id),
    year       INT            NOT NULL,
    month      INT            NOT NULL CHECK (month >= 1 AND month <= 12),
    amount     NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT budgets_user_year_month_unique UNIQUE (user_id, year, month)
);
