-- El auto del conductor: una referencia al catálogo desde el perfil (RF01).
--
-- **Una columna en users y no una tabla `driver_vehicles`.** Un conductor tiene UN auto: es lo
-- que muestran la portada ("tu auto"), la ficha del perfil y el conteo de estaciones
-- compatibles, todos en singular. Una tabla intermedia agregaría un ABM entero y un concepto de
-- "cuál estoy usando ahora" para modelar algo que ninguna pantalla pide.
--
-- **NULL es un estado legítimo, no un dato faltante.** Todas las cuentas que ya existen nacen
-- sin auto, y alguien puede querer sacárselo. Las pantallas que dependen del vehículo tienen que
-- saber decir "todavía no elegiste tu auto", que es distinto de mostrar una ficha con guiones.
--
-- **Acá sí va clave foránea**, a diferencia de stations.owner_id o payment_methods.driver_id.
-- Aquellas apuntan a otro artefacto y una FK los ataría a compartir base para siempre; esta
-- queda entre dos tablas del mismo schema (core) y del mismo módulo, así que la base puede
-- sostener la integridad sin atar nada.
ALTER TABLE users ADD COLUMN vehicle_model_id BIGINT;

ALTER TABLE users
    ADD CONSTRAINT fk_users_vehicle_model
    FOREIGN KEY (vehicle_model_id) REFERENCES vehicle_models (id);
