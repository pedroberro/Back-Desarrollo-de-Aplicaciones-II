-- Security audit log (SEC-05): who did what, from where, and how it ended. Never passwords,
-- tokens or card data: detalle is a short human description.
CREATE TABLE auditoria (
    id         BIGSERIAL PRIMARY KEY,
    fecha      TIMESTAMP    NOT NULL,
    evento     VARCHAR(40)  NOT NULL,
    resultado  VARCHAR(10)  NOT NULL,
    usuario    VARCHAR(150),
    ip         VARCHAR(64),
    user_agent VARCHAR(300),
    detalle    VARCHAR(300)
);

CREATE INDEX idx_auditoria_fecha ON auditoria (fecha DESC);
CREATE INDEX idx_auditoria_usuario ON auditoria (usuario, fecha DESC);
