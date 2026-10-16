-- Messages of the charge queue already handled (SEC-08). A redelivered copy finds its id here and
-- is skipped, on any instance and after any restart.
CREATE TABLE mensajes_procesados (
    mensaje_id   VARCHAR(64) PRIMARY KEY,
    reserva_id   BIGINT,
    procesado_el TIMESTAMP   NOT NULL
);
