CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    provider    VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email       VARCHAR(255),
    display_name VARCHAR(255) NOT NULL,
    avatar_url  VARCHAR(512),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_provider_provider_user_id UNIQUE (provider, provider_user_id)
);
