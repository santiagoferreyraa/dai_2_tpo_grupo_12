-- La versión de cada estación y de cada usuario, para el ETag de sus actualizaciones (QA-30, ECO-180).
--
-- Hibernate la sube en cada UPDATE (@Version) y la usa en el WHERE: si otro guardó en el medio, el
-- UPDATE no toca ninguna fila y la escritura se rechaza en vez de pisar lo del otro. La API la publica
-- como ETag, y un PUT con un If-Match viejo responde 412.
--
-- Las filas que ya existen arrancan en 0: para el primer If-Match da lo mismo el número, solo importa
-- que el que se leyó sea el que sigue guardado.
ALTER TABLE stations ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
