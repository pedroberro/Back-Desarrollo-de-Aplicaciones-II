-- Follow-up of the asynchronous charge (see BarridoCobros). cobro_solicitado_el is set when a
-- booking enters the charge queue and every time it is requested again; NULL means it never did
-- (bookings made with messaging off, or the demo ones), and those are left alone.
ALTER TABLE reservas ADD COLUMN cobro_solicitado_el TIMESTAMP;
ALTER TABLE reservas ADD COLUMN reencolados_cobro INT NOT NULL DEFAULT 0;

CREATE INDEX idx_reservas_cobro_sin_resolver ON reservas (cobro_solicitado_el)
    WHERE estado = 'PENDIENTE' AND cobro_solicitado_el IS NOT NULL;
