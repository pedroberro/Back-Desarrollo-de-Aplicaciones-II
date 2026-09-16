-- Accounts created from now on start as PENDIENTE until the email is confirmed.
-- Existing accounts keep their state (ACTIVO), so nobody is locked out.
CREATE TABLE email_confirmation_tokens (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL REFERENCES usuarios (id),
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
