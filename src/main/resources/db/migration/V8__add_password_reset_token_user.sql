ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES usuarios(id);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_id
    ON password_reset_tokens(user_id);
