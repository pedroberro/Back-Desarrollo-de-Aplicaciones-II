-- Compatibility migration for databases created before password-reset tokens
-- were linked to usuarios with the current column name.
ALTER TABLE password_reset_tokens
    ADD COLUMN IF NOT EXISTS usuario_id BIGINT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'password_reset_tokens'
          AND column_name = 'user_id'
    ) THEN
        EXECUTE '
            UPDATE password_reset_tokens
            SET usuario_id = user_id
            WHERE usuario_id IS NULL
        ';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_password_reset_tokens_usuario'
    ) THEN
        ALTER TABLE password_reset_tokens
            ADD CONSTRAINT fk_password_reset_tokens_usuario
            FOREIGN KEY (usuario_id) REFERENCES usuarios(id);
    END IF;
END $$;
