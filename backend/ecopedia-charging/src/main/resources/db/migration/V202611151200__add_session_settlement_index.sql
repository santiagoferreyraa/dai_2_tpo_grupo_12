-- Índice para el barrido asincrónico de sesiones completadas pendientes de liquidación (RNF06, ECO-55).
CREATE INDEX idx_charging_sessions_settlement ON charging_sessions (status, payment_status);
