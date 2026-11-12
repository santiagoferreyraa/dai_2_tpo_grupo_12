-- Estado de liquidación y costos asociados en la sesión de carga (RNF06, ECO-55)
ALTER TABLE charging_sessions ADD COLUMN payment_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUIRED';
ALTER TABLE charging_sessions ADD COLUMN real_cost NUMERIC(10, 2);
ALTER TABLE charging_sessions ADD COLUMN additional_charge NUMERIC(10, 2);

CREATE INDEX idx_charging_sessions_settlement ON charging_sessions (status, payment_status);
