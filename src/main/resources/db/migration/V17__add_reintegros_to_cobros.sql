-- Refunds of a charge, under the cancellation policy (see PoliticaCancelacion). A charge is
-- refunded at most once: cancelling is final.
ALTER TABLE cobros ADD COLUMN monto_reintegrado   NUMERIC(12,2) CHECK (monto_reintegrado >= 0);
ALTER TABLE cobros ADD COLUMN reintegro_referencia VARCHAR(80);
ALTER TABLE cobros ADD COLUMN reintegrado_el       TIMESTAMP;
