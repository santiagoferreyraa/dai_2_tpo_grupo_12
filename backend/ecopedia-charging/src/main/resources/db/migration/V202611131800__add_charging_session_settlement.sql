-- La liquidación de la sesión de carga, guardada en la sesión (RF14, ECO-44).
--
-- Desde ECO-55 la sesión se cierra y se liquida, pero el resultado solo viajaba en la respuesta del
-- stop. Guardarlo permite que detener una sesión ya cerrada —un reintento, o el conductor que aprieta
-- después del corte automático— devuelva lo mismo que la primera vez, y que la pantalla la muestre
-- después.
--
-- Todo admite NULL: una sesión activa todavía no tiene liquidación, y las cerradas antes de esta
-- migración tampoco.

ALTER TABLE charging_sessions ADD COLUMN real_cost NUMERIC(10, 2);
ALTER TABLE charging_sessions ADD COLUMN deposit_applied NUMERIC(10, 2);
ALTER TABLE charging_sessions ADD COLUMN final_billed NUMERIC(10, 2);
ALTER TABLE charging_sessions ADD COLUMN additional_charge NUMERIC(10, 2);
ALTER TABLE charging_sessions ADD COLUMN payment_status VARCHAR(20);

-- "¿Tiene este conductor una sesión activa?": se pregunta en cada habilitación y cada vez que el
-- front abre la pantalla de carga.
CREATE INDEX idx_charging_sessions_driver_status ON charging_sessions (driver_id, status);
