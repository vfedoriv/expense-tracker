CREATE TABLE budget_alert_states (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    year                SMALLINT NOT NULL,
    month               SMALLINT NOT NULL CHECK (month BETWEEN 1 AND 12),
    threshold_50_fired  BOOLEAN NOT NULL DEFAULT FALSE,
    threshold_80_fired  BOOLEAN NOT NULL DEFAULT FALSE,
    threshold_100_fired BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_budget_alert_states_user_id_year_month UNIQUE (user_id, year, month)
);
