-- Floor layouts. A floor is no longer "rows of cubicles around one corridor": its plan is any set
-- of rectangles on the grid. Spaces and services already were; the structure becomes data too:
-- corridors, the elevator core, support areas (lockers, building services) and open-air areas
-- (terrace, patio). scripts/generar_disposiciones.py lays the demo floors out (V15).

ALTER TABLE pisos ADD COLUMN disposicion VARCHAR(20) NOT NULL DEFAULT 'PASILLO_CENTRAL';

-- Grid height, explicit instead of derived from whatever is drawn lowest.
ALTER TABLE pisos ADD COLUMN plano_filas INT;
UPDATE pisos p SET plano_filas = GREATEST(
    COALESCE((SELECT MAX(e.plano_y + e.plano_alto) FROM espacios e WHERE e.piso_id = p.id), 0),
    COALESCE((SELECT MAX(s.plano_y + s.plano_alto) FROM servicios_piso s WHERE s.piso_id = p.id), 0),
    COALESCE(p.fila_pasillo + 1, 0));
ALTER TABLE pisos ALTER COLUMN plano_filas SET NOT NULL;

CREATE TABLE areas_plano (
    id          BIGSERIAL PRIMARY KEY,
    piso_id     BIGINT      NOT NULL REFERENCES pisos (id),
    tipo        VARCHAR(20) NOT NULL,
    etiqueta    VARCHAR(40) NOT NULL,
    plano_x     INT         NOT NULL,
    plano_y     INT         NOT NULL,
    plano_ancho INT         NOT NULL,
    plano_alto  INT         NOT NULL DEFAULT 1
);

CREATE INDEX idx_areas_plano_piso ON areas_plano (piso_id);

-- The single corridor of every existing floor becomes its first area.
INSERT INTO areas_plano (piso_id, tipo, etiqueta, plano_x, plano_y, plano_ancho, plano_alto)
SELECT id, 'PASILLO', 'Pasillo', 0, fila_pasillo, plano_columnas, 1 FROM pisos WHERE fila_pasillo IS NOT NULL;

ALTER TABLE pisos DROP COLUMN fila_pasillo;
