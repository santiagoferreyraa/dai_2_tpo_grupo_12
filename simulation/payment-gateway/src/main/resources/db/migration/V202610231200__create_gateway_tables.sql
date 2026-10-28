-- Las tablas de la pasarela simulada. Es otro sistema, con su propia base: Ecopedia no la ve.
--
-- Nombre con timestamp, NUNCA V1/V2/V3. Ver README, "Migraciones".

-- Las tarjetas tokenizadas. NO hay columna para el número, y es a propósito: para cobrar con el
-- token alcanza con la marca, el vencimiento y el comportamiento de la tarjeta de prueba, que se
-- decide al tokenizar. El número se descarta ahí mismo. Si alguien se lleva este archivo, no hay
-- ninguna tarjeta que sacar.
CREATE TABLE card_tokens (
    id           VARCHAR(40) PRIMARY KEY,
    brand        VARCHAR(20) NOT NULL,
    last_four    VARCHAR(4)  NOT NULL,
    expiry_month INTEGER     NOT NULL,
    expiry_year  INTEGER     NOT NULL,
    behavior     VARCHAR(30) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Los cobros, también los rechazados. La clave de idempotencia es única: con la misma clave, el
-- mismo cobro, nunca dos.
CREATE TABLE payments (
    id                 VARCHAR(40)    PRIMARY KEY,
    idempotency_key    VARCHAR(100)   NOT NULL UNIQUE,
    card_token_id      VARCHAR(40)    NOT NULL REFERENCES card_tokens (id),
    amount             NUMERIC(12, 2) NOT NULL,
    refunded_amount    NUMERIC(12, 2) NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    status_detail      VARCHAR(60)    NOT NULL,
    description        VARCHAR(255),
    external_reference VARCHAR(100),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE refunds (
    id              VARCHAR(40)    PRIMARY KEY,
    idempotency_key VARCHAR(100)   NOT NULL UNIQUE,
    payment_id      VARCHAR(40)    NOT NULL REFERENCES payments (id),
    amount          NUMERIC(12, 2) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);
