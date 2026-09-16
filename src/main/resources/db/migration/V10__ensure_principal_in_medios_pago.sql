-- Compatibility migration for databases that had version 8 applied before
-- the primary-payment-method feature was introduced.
ALTER TABLE medios_pago
    ADD COLUMN IF NOT EXISTS principal BOOLEAN NOT NULL DEFAULT FALSE;

-- Preserve the intended invariant for pre-existing cards as well.
UPDATE medios_pago mp
SET principal = TRUE
WHERE mp.id IN (
    SELECT DISTINCT ON (usuario_id) id
    FROM medios_pago
    WHERE eliminado = FALSE
    ORDER BY usuario_id, creado_el, id
)
AND NOT EXISTS (
    SELECT 1
    FROM medios_pago existing
    WHERE existing.usuario_id = mp.usuario_id
      AND existing.eliminado = FALSE
      AND existing.principal = TRUE
);
