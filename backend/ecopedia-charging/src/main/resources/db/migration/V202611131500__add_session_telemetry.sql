-- Lo último que informó el cargador sobre cada sesión (ECO-40, RF16).
--
-- SesionesDeCarga escucha el tópico telemetry.charging y guarda acá la última lectura: los kWh
-- acumulados ya tenían columna (kwh_delivered); se suman la potencia, el porcentaje de batería y el
-- instante de la lectura. charger_end_reason queda en NULL mientras el cargador sigue cargando, y
-- guarda por qué terminó cuando avisa que cortó (STOPPED, BATTERY_FULL o FAULT): es el dato con el
-- que se decide cerrar una sesión que el cargador terminó por su cuenta.

ALTER TABLE charging_sessions ADD COLUMN current_power_kw NUMERIC(6, 2);
ALTER TABLE charging_sessions ADD COLUMN battery_percent NUMERIC(5, 2);
ALTER TABLE charging_sessions ADD COLUMN last_reading_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE charging_sessions ADD COLUMN charger_end_reason VARCHAR(20);
