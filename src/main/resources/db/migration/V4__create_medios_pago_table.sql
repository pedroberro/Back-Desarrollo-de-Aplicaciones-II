CREATE TABLE medios_pago (
    id                    BIGSERIAL PRIMARY KEY,
    usuario_id            BIGINT       NOT NULL REFERENCES usuarios (id),
    tipo                  VARCHAR(30)  NOT NULL,
    descripcion           VARCHAR(200) NOT NULL,
    numero_tarjeta_masked VARCHAR(23)  NOT NULL,
    titular               VARCHAR(100) NOT NULL,
    vencimiento           VARCHAR(5)   NOT NULL,
    verificado            BOOLEAN      NOT NULL DEFAULT FALSE,
    eliminado             BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_el             TIMESTAMP    NOT NULL DEFAULT NOW()
);