-- Importe efectivamente devuelto sobre la seña cuando el reintegro es parcial por falla en curso (RF13, ECO-59).
ALTER TABLE deposit_charges
    ADD COLUMN refunded_amount NUMERIC(10, 2);
