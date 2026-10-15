-- Card the customer chose to pay the booking with. NULL means "the customer's main card at
-- charge time", which is what bookings loaded by an administrator use.
ALTER TABLE reservas ADD COLUMN medio_pago_id BIGINT REFERENCES medios_pago (id);

-- One charge per booking: the UNIQUE makes a redelivered message unable to charge twice.
-- If an administrator deletes the booking, the charge stays on record with reserva_id NULL.
CREATE TABLE cobros (
    id             BIGSERIAL PRIMARY KEY,
    reserva_id     BIGINT        UNIQUE REFERENCES reservas (id) ON DELETE SET NULL,
    medio_pago_id  BIGINT        REFERENCES medios_pago (id),
    monto          NUMERIC(12,2) NOT NULL CHECK (monto >= 0),
    estado         VARCHAR(20)   NOT NULL,
    referencia     VARCHAR(80),
    motivo_rechazo VARCHAR(200),
    procesado_el   TIMESTAMP     NOT NULL
);
