-- Las tareas que corren una sola vez en la vida de una base, y que no son esquema.
--
-- Hoy hay una: repartir las estaciones sembradas entre los CPO cuando se crea la cuenta CPO
-- inicial (ver InitialSetup). La fila es la marca de que ya se hizo: aunque después se cree otra
-- cuenta inicial con otro mail, el reparto no se repite.
--
-- No va como migración de datos porque depende de lo que haya en cada base (cuántos CPO, qué
-- contraseñas trae el .env), y una migración es igual para todas.

CREATE TABLE one_time_tasks (
    name    VARCHAR(80)              PRIMARY KEY,
    done_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
