ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS used_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();

CREATE UNIQUE INDEX IF NOT EXISTS uk_password_reset_tokens_hash
    ON password_reset_tokens(token_hash);
