-- Importe a devolver de la seña cuando corresponde reintegro parcial por falla en curso (RF13, ECO-59).
ALTER TABLE bookings
    ADD COLUMN deposit_refund_amount NUMERIC(10, 2);
