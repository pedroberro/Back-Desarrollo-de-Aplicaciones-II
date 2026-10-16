-- Rotating refresh tokens (SEC-02). One family per sign-in; every refresh spends its token and
-- issues the next one in the same family. Presenting a spent token means it was copied: the
-- whole family is revoked. Only the SHA-256 of the token is stored.
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL REFERENCES usuarios (id),
    familia     UUID        NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    creado_el   TIMESTAMP   NOT NULL,
    expira_el   TIMESTAMP   NOT NULL,
    usado_el    TIMESTAMP,
    revocado_el TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_familia ON refresh_tokens (familia);
CREATE INDEX idx_refresh_tokens_usuario ON refresh_tokens (usuario_id);
