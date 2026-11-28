-- El auto del conductor deja de ser una relación entre tablas (RNF09).
--
-- Usuarios guarda solo el id del modelo y le pide la ficha a VehicleService, la interfaz pública
-- de Vehículos: un componente no ata su esquema a las tablas de otro. Que el id exista lo valida
-- el servicio al guardarlo, igual que Pagos con driver_id o Terminales con owner_id.
--
-- La columna y sus datos quedan como están: solo se va la clave foránea.
ALTER TABLE users DROP CONSTRAINT fk_users_vehicle_model;
