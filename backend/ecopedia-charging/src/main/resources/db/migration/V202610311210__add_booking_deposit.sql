-- La seña de cada reserva, la clave del checkout y la incomparecencia (ECO-53, ECO-179, ECO-57).
--
-- Nombre con timestamp, NUNCA V1/V2/V3. Ver README, "Migraciones".
--
-- Las reservas que ya existían quedan sin seña (deposit_amount 0, deposit_status NONE): se
-- confirmaron cuando el checkout todavía no cobraba, y es lo que de verdad pasó.

-- Lo que se cobró al confirmar. 0 si el esquema tarifario no pide seña.
ALTER TABLE bookings ADD COLUMN deposit_amount NUMERIC(10, 2) NOT NULL DEFAULT 0;

-- NONE, CHARGED, REFUND_PENDING, REFUNDED o FORFEITED. Ver DepositStatus.
ALTER TABLE bookings ADD COLUMN deposit_status VARCHAR(20) NOT NULL DEFAULT 'NONE';

-- El cobro en Pagos (ecopedia-integration), para pedir la devolución. Sin clave foránea: es de
-- otro artefacto, igual que connector_id y driver_id.
ALTER TABLE bookings ADD COLUMN deposit_charge_id BIGINT;

-- Los últimos cuatro de la tarjeta con la que se cobró, para el aviso de la devolución.
ALTER TABLE bookings ADD COLUMN deposit_card_last_four VARCHAR(4);

-- Hasta cuándo cancelar devuelve la seña. Se fija al confirmar y no se recalcula: si mañana
-- cambia el plazo, la reserva de hoy se rige por lo que se le dijo al conductor al pagar.
ALTER TABLE bookings ADD COLUMN deposit_refundable_until TIMESTAMP WITH TIME ZONE;

-- La Idempotency-Key con la que se confirmó, y la retención que confirmó. Un checkout repetido con
-- la misma clave devuelve esta reserva en vez de cobrar y confirmar otra (QA-29).
ALTER TABLE bookings ADD COLUMN checkout_key VARCHAR(100);
ALTER TABLE bookings ADD COLUMN hold_id VARCHAR(36);

-- Bloqueo optimista: la cancelación del conductor y el barrido de la incomparecencia pueden tocar
-- la misma reserva a la vez, y sin esto la última escritura pisa a la primera.
ALTER TABLE bookings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Una clave por conductor nombra una sola reserva. Las filas sin clave (las anteriores, las que
-- no pasan por el checkout) no chocan entre sí: NULL no es igual a NULL.
CREATE UNIQUE INDEX uq_bookings_driver_checkout_key ON bookings (driver_id, checkout_key);

-- Lo que preguntan los dos barridos: las confirmadas que ya empezaron y las señas por devolver.
CREATE INDEX idx_bookings_status_start ON bookings (status, window_start);
CREATE INDEX idx_bookings_deposit_status ON bookings (deposit_status);
