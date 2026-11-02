-- El nodo de la distribuidora al que está conectada cada estación (RF15).
--
-- Antes de habilitar una carga rápida, SesionesDeCarga le pregunta a la distribuidora si el nodo
-- aguanta la potencia pedida, y la distribuidora identifica sus nodos con su propio código: el que
-- figura en el contrato de conexión del operador. Por eso es texto libre y no una clave foránea:
-- el catálogo de nodos es de la distribuidora, un sistema externo, y Ecopedia no lo tiene.
--
-- Opcional a propósito: una estación sin nodo existe igual y ofrece carga normal; la que no puede
-- ofrecer es la rápida, porque no hay a quién consultar.
ALTER TABLE stations ADD COLUMN node_id VARCHAR(40);

-- Las 15 estaciones del seed, repartidas entre los nodos de prueba de la distribuidora simulada
-- (simulation/power-grid, ver su README) para que haya al menos una de cada comportamiento:
--
--   CABA-CENTRO y CABA-NORTE   autorizan hasta su capacidad
--   CABA-OESTE                 tope de demanda vigente: autoriza hasta 50 kW
--   CABA-SUR                   en mantenimiento: no autoriza nada (La Boca ya tiene sus dos
--                              conectores fuera de servicio, así que el dato es coherente)
--   GBA-NORTE                  autoriza hasta su capacidad
--   LA-PLATA                   contesta, pero tarda más que el timeout de Ecopedia
--
-- Por id y no por nombre: el seed los fijó a mano. En un volumen con estaciones cargadas antes del
-- seed, el UPDATE le pone nodo a la que tenga ese id, que es inofensivo.
UPDATE stations SET node_id = 'CABA-CENTRO' WHERE id IN (1, 2, 4, 5);
UPDATE stations SET node_id = 'CABA-NORTE' WHERE id IN (3, 6, 7, 8);
UPDATE stations SET node_id = 'CABA-OESTE' WHERE id IN (9, 10);
UPDATE stations SET node_id = 'CABA-SUR' WHERE id = 11;
UPDATE stations SET node_id = 'GBA-NORTE' WHERE id IN (12, 13, 14);
UPDATE stations SET node_id = 'LA-PLATA' WHERE id = 15;
