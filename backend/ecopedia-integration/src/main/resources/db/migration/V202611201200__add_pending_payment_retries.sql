-- Reintentos de la deuda (ECO-62, decidido por Manuel el 20/11): después del cobro original, tres
-- reintentos cada 20 minutos; si fallan los tres, la deuda queda vencida (DEFAULTED), se anulan las
-- reservas confirmadas del conductor y solo se salda desde el perfil.
--
-- retry_count        cuántos reintentos fallaron (el cobro original no cuenta).
-- next_attempt_at    cuándo toca el próximo; nulo en una deuda vieja = ya mismo, o sin más reintentos.
-- last_attempt_at    cuándo fue el último.
-- claimed_until      quién está cobrando: el barrido o el conductor desde el perfil. Mientras no
--                    venza, nadie más cobra esta deuda (que no salgan dos cobros a la vez).
-- bookings_cancelled si ya se le pidió a Reservas que anule las reservas (deuda vencida).
-- gateway_payment_id el cobro de la pasarela que la saldó.

ALTER TABLE pending_payments ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE pending_payments ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pending_payments ADD COLUMN last_attempt_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE pending_payments ADD COLUMN claimed_until TIMESTAMP WITH TIME ZONE;
ALTER TABLE pending_payments ADD COLUMN bookings_cancelled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pending_payments ADD COLUMN gateway_payment_id VARCHAR(80);

CREATE INDEX idx_pending_payments_status_next_attempt ON pending_payments (status, next_attempt_at);
