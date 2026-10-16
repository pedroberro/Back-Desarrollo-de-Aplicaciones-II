-- Second factor (SEC-03): TOTP secret, whether it was confirmed, and the last time step used,
-- so a code cannot be replayed within its 30-second window.
ALTER TABLE usuarios ADD COLUMN segundo_factor_secreto TEXT;
ALTER TABLE usuarios ADD COLUMN segundo_factor_activo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuarios ADD COLUMN segundo_factor_ultimo_paso BIGINT;
