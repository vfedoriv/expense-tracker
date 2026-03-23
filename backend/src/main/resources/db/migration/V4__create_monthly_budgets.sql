CREATE TABLE monthly_budgets (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    year        SMALLINT NOT NULL,
    month       SMALLINT NOT NULL CHECK (month BETWEEN 1 AND 12),
    amount      DECIMAL(12,2) NOT NULL CHECK (amount > 0),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_monthly_budgets_user_id_year_month UNIQUE (user_id, year, month)
);
