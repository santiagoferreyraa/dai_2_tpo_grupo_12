-- Por qué se cerró cada sesión de carga (RF12, ECO-45): la detuvo el conductor, llegó al porcentaje
-- objetivo o se terminó la ventana. Es lo que la pantalla le explica al conductor cuando la carga
-- terminó sola. NULL mientras la sesión sigue activa, y en las cerradas antes de esta migración.

ALTER TABLE charging_sessions ADD COLUMN stop_reason VARCHAR(20);
