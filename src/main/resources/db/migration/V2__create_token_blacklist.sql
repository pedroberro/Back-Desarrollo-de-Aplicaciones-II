CREATE TABLE token_blacklist (
    id         BIGSERIAL PRIMARY KEY,
    token      VARCHAR(500) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);