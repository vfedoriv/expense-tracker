CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    provider        VARCHAR(50)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    display_name    VARCHAR(255) NOT NULL,
    avatar_url      VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT users_provider_unique UNIQUE (provider, provider_user_id)
);
