-- El cierre de la sesión de carga (RF11, RF14, ECO-44).
--
-- Hasta ECO-60 la sesión nacía y nadie la cerraba. Ahora la cierra el conductor desde la aplicación
-- (y, con ECO-45, el corte automático), y queda anotado por qué terminó, en qué porcentaje quedó la
-- batería y cómo se liquidó: el consumo, la seña que se descuenta, lo que se cobró y si el cobro
-- salió o quedó como deuda (RNF06).
--
-- Los importes admiten NULL: una sesión activa todavía no tiene, y una cerrada sin respuesta de
-- Tarificación tampoco. payment_status no: arranca en NOT_SETTLED, también para las que ya había.

ALTER TABLE charging_sessions ADD COLUMN battery_percent NUMERIC(5, 2);
ALTER TABLE charging_sessions ADD COLUMN end_reason VARCHAR(20);
ALTER TABLE charging_sessions ADD COLUMN real_cost NUMERIC(10, 2);
ALTER TABLE charging_sessions ADD COLUMN deposit_applied NUMERIC(10, 2);
ALTER TABLE charging_sessions ADD COLUMN final_amount NUMERIC(10, 2);
ALTER TABLE charging_sessions ADD COLUMN amount_to_charge NUMERIC(10, 2);
ALTER TABLE charging_sessions ADD COLUMN payment_status VARCHAR(20) DEFAULT 'NOT_SETTLED' NOT NULL;

-- "¿Tiene este conductor una sesión activa?": se pregunta en cada habilitación y cada vez que el
-- front abre la pantalla de carga.
CREATE INDEX idx_charging_sessions_driver_status ON charging_sessions (driver_id, status);
