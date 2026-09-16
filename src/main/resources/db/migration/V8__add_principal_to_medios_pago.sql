ALTER TABLE medios_pago ADD COLUMN principal BOOLEAN NOT NULL DEFAULT FALSE;

-- The oldest active card of each user becomes the primary one.
UPDATE medios_pago mp
SET principal = TRUE
WHERE mp.id IN (
    SELECT DISTINCT ON (usuario_id) id
    FROM medios_pago
    WHERE eliminado = FALSE
    ORDER BY usuario_id, creado_el, id
);
