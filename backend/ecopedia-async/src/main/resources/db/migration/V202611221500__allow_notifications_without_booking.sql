-- Los avisos de una deuda (ECO-62) no son de ninguna reserva: no tienen reserva, conector ni ventana.
-- Hasta ahora todo aviso era de una reserva y esas cuatro columnas eran obligatorias.
--
-- Nombre con timestamp, NUNCA V1/V2/V3. Ver README, "Migraciones".

ALTER TABLE notifications ALTER COLUMN booking_id DROP NOT NULL;
ALTER TABLE notifications ALTER COLUMN connector_id DROP NOT NULL;
ALTER TABLE notifications ALTER COLUMN window_start DROP NOT NULL;
ALTER TABLE notifications ALTER COLUMN window_end DROP NOT NULL;
