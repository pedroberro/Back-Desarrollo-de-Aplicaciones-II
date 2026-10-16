-- Encrypted fields (SEC-09) are longer than their plaintext: version, IV and tag in base64.
-- Existing plaintext values are encrypted by RecifradoCampos on the next startup.
ALTER TABLE usuarios ALTER COLUMN telefono TYPE TEXT;
ALTER TABLE medios_pago ALTER COLUMN titular TYPE TEXT;
ALTER TABLE medios_pago ALTER COLUMN vencimiento TYPE TEXT;
