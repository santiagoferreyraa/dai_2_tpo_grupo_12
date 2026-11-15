-- Código de acceso de cada conector (RF11, ECO-44).
--
-- Es el PIN de 6 dígitos que la terminal muestra impreso, y lo mismo que lleva su QR
-- ("ecopedia:connector:<código>"). El conductor lo escanea o lo tipea para iniciar la carga, y con
-- eso acredita que está parado frente a ESE conector: el id no alcanza, porque se ve en el mapa.
--
-- Los conectores que ya existen (el seed y los que haya cargado cada uno) reciben un código
-- derivado del id: id * 7919 + 104729 módulo un millón. 7919 es primo y no divide a 10^6, así que
-- dos ids distintos por debajo del millón nunca dan el mismo código, y el índice único no choca.
-- Es predecible, y está bien para los datos de prueba; los conectores nuevos lo reciben al azar
-- (ver Connector#assignAccessCode), y el operador puede regenerarlo cuando quiera.
--
-- LPAD, MOD y CAST se escriben igual en PostgreSQL y en H2.

ALTER TABLE connectors ADD COLUMN access_code VARCHAR(6);

UPDATE connectors
   SET access_code = LPAD(CAST(MOD(id * 7919 + 104729, 1000000) AS VARCHAR(6)), 6, '0');

ALTER TABLE connectors ALTER COLUMN access_code SET NOT NULL;

CREATE UNIQUE INDEX uq_connectors_access_code ON connectors (access_code);
