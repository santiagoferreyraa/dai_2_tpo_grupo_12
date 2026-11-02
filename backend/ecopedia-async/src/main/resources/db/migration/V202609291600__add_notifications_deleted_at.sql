-- Borrado de avisos por parte del conductor: el aviso se marca, no se borra de la tabla.
--
-- Nombre con timestamp, NUNCA V1/V2/V3. Ver README, "Migraciones".
-- Sin calificar el schema: lo pone Flyway (spring.flyway.schemas=async).

-- Cuándo lo borró el conductor. Nulo mientras siga en su buzón, que es el caso de todos los
-- avisos que ya existen: la columna nace nula y nadie desaparece al aplicar esta migración.
--
-- **Marca y no DELETE, y el motivo es event_id.** La deduplicación de la cola es
-- `existsByEventId` contra la restricción UNIQUE de esa columna (ver la migración
-- create_notifications). Con un DELETE de verdad, la fila se va y con ella la prueba de que ese
-- mensaje ya se procesó: una reentrega del mismo mensaje —que el listener contempla y que en JMS
-- pasa— no encontraría nada, volvería a guardar el aviso que el conductor borró Y volvería a
-- mandarle el mail, porque el mail sale después de guardar. Con la marca, la fila sigue estando,
-- existsByEventId sigue diciendo que sí, y la reentrega se descarta como cualquier otra.
--
-- El precio es que toda consulta de la pantalla tiene que filtrar `deleted_at IS NULL`, y por eso
-- los métodos del repositorio lo llevan en el nombre.
ALTER TABLE notifications ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
