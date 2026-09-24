-- Catalog hierarchy: empresa > edificio > piso > espacio. Floor services (kitchen, lounge,
-- bathrooms) live apart because they are drawn on the plan but never booked.
-- Plan coordinates are grid cells: plano_x / plano_ancho over piso.plano_columnas,
-- plano_y / plano_alto over rows, with the corridor at piso.fila_pasillo.

CREATE TABLE empresas (
    id     BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    rubro  VARCHAR(100) NOT NULL,
    tono   VARCHAR(10)  NOT NULL DEFAULT 'indigo'
);

CREATE TABLE edificios (
    id            BIGSERIAL PRIMARY KEY,
    empresa_id    BIGINT       NOT NULL REFERENCES empresas (id),
    nombre        VARCHAR(100) NOT NULL,
    direccion     VARCHAR(150) NOT NULL,
    localidad     VARCHAR(80)  NOT NULL,
    hora_apertura TIME         NOT NULL,
    hora_cierre   TIME         NOT NULL,
    tono          VARCHAR(10)  NOT NULL DEFAULT 'indigo',
    CHECK (hora_cierre > hora_apertura)
);

CREATE TABLE pisos (
    id             BIGSERIAL PRIMARY KEY,
    edificio_id    BIGINT      NOT NULL REFERENCES edificios (id),
    numero         INT         NOT NULL,
    nombre         VARCHAR(40) NOT NULL,
    luz_natural    BOOLEAN     NOT NULL DEFAULT FALSE,
    ventanas       VARCHAR(40) NOT NULL DEFAULT '',
    plano_columnas INT         NOT NULL DEFAULT 42,
    fila_pasillo   INT,
    UNIQUE (edificio_id, numero)
);

CREATE TABLE espacios (
    id            BIGSERIAL PRIMARY KEY,
    piso_id       BIGINT       NOT NULL REFERENCES pisos (id),
    codigo        VARCHAR(20)  NOT NULL,
    tipo          VARCHAR(30)  NOT NULL,
    capacidad     INT          NOT NULL CHECK (capacidad > 0),
    ventana       BOOLEAN      NOT NULL DEFAULT FALSE,
    descripcion   TEXT         NOT NULL,
    superficie    NUMERIC(5, 1) NOT NULL,
    orientacion   VARCHAR(20)  NOT NULL,
    ubicacion     VARCHAR(80)  NOT NULL,
    ruido         VARCHAR(80)  NOT NULL,
    climatizacion VARCHAR(80)  NOT NULL,
    equipamiento  JSONB        NOT NULL DEFAULT '[]',
    plano_x       INT          NOT NULL,
    plano_y       INT          NOT NULL,
    plano_ancho   INT          NOT NULL,
    plano_alto    INT          NOT NULL DEFAULT 1,
    UNIQUE (piso_id, codigo)
);

CREATE TABLE servicios_piso (
    id          BIGSERIAL PRIMARY KEY,
    piso_id     BIGINT      NOT NULL REFERENCES pisos (id),
    tipo        VARCHAR(20) NOT NULL,
    nombre      VARCHAR(40) NOT NULL,
    plano_x     INT         NOT NULL,
    plano_y     INT         NOT NULL,
    plano_ancho INT         NOT NULL,
    plano_alto  INT         NOT NULL DEFAULT 1
);

CREATE INDEX idx_edificios_empresa ON edificios (empresa_id);
CREATE INDEX idx_pisos_edificio ON pisos (edificio_id);
CREATE INDEX idx_espacios_piso ON espacios (piso_id);
CREATE INDEX idx_servicios_piso ON servicios_piso (piso_id);

-- Bookings. Two active bookings of the same space can never overlap: the service checks it
-- with the space row locked, and this exclusion constraint is the last line of defence.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE reservas (
    id             BIGSERIAL PRIMARY KEY,
    espacio_id     BIGINT      NOT NULL REFERENCES espacios (id),
    usuario_id     BIGINT      NOT NULL REFERENCES usuarios (id),
    creada_por_id  BIGINT      NOT NULL REFERENCES usuarios (id),
    inicio         TIMESTAMP   NOT NULL,
    fin            TIMESTAMP   NOT NULL,
    estado         VARCHAR(20) NOT NULL,
    creada_el      TIMESTAMP   NOT NULL DEFAULT NOW(),
    actualizada_el TIMESTAMP   NOT NULL DEFAULT NOW(),
    CHECK (fin > inicio),
    CONSTRAINT reservas_sin_superposicion EXCLUDE USING gist (
        espacio_id WITH =,
        tsrange(inicio, fin) WITH &&
    ) WHERE (estado IN ('PENDIENTE', 'CONFIRMADA'))
);

CREATE INDEX idx_reservas_espacio_inicio ON reservas (espacio_id, inicio);
CREATE INDEX idx_reservas_usuario_inicio ON reservas (usuario_id, inicio);
CREATE INDEX idx_reservas_inicio ON reservas (inicio);

-- Audit trail: every change an administrator makes to someone else's booking, with its reason.
CREATE TABLE reserva_cambios (
    id         BIGSERIAL PRIMARY KEY,
    reserva_id BIGINT       NOT NULL REFERENCES reservas (id) ON DELETE CASCADE,
    autor_id   BIGINT       NOT NULL REFERENCES usuarios (id),
    motivo     VARCHAR(500) NOT NULL,
    detalle    VARCHAR(500) NOT NULL,
    creado_el  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reserva_cambios_reserva ON reserva_cambios (reserva_id);

-- A block occupies the calendar like a booking but has no customer. Blocking a whole floor
-- creates one row per bookable space, all sharing the same grupo.
CREATE TABLE bloqueos (
    id            BIGSERIAL PRIMARY KEY,
    espacio_id    BIGINT       NOT NULL REFERENCES espacios (id),
    inicio        TIMESTAMP    NOT NULL,
    fin           TIMESTAMP    NOT NULL,
    motivo        VARCHAR(20)  NOT NULL,
    detalle       VARCHAR(200) NOT NULL DEFAULT '',
    grupo         UUID         NOT NULL,
    creado_por_id BIGINT       NOT NULL REFERENCES usuarios (id),
    creado_el     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CHECK (fin > inicio),
    CONSTRAINT bloqueos_sin_superposicion EXCLUDE USING gist (
        espacio_id WITH =,
        tsrange(inicio, fin) WITH &&
    )
);

CREATE INDEX idx_bloqueos_espacio_inicio ON bloqueos (espacio_id, inicio);
