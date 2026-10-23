-- El nombre del archivo de la foto de cada modelo, para el selector visual.
--
-- **Va como migración nueva y no editando la que creó la tabla**, aunque esa todavía no esté en
-- ningún merge: quien ya levantó el proyecto con ella la tiene aplicada y con su checksum
-- guardado, así que tocarla le rompe el arranque. La regla del README no distingue casos.
--
-- **Se guarda el SLUG y no la URL entera.** Una columna con "/vehicles/models/tesla-model-3.png"
-- mete una ruta del frontend adentro de la base: el día que las imágenes se muevan de carpeta o
-- salgan de un CDN, hay que migrar datos en vez de cambiar una función. Lo que identifica a la
-- foto es el nombre; dónde vive es decisión de quien la muestra.
--
-- **Sin columna para el logo de la marca**, que es la otra imagen del selector. No sería un dato
-- del modelo sino de la marca, y repetirlo en cada fila deja diecinueve lugares donde puede
-- quedar escrito distinto. El logo se resuelve a partir del nombre de la marca; ver `brandSlug`
-- en el frontend.
ALTER TABLE vehicle_models ADD COLUMN image_slug VARCHAR(60);

-- Los modelos que ya estaban. El slug es marca y modelo en minúscula, con guiones: es el mismo
-- criterio con el que se derivan los de la marca, así que no hay dos formas de nombrar un archivo.
UPDATE vehicle_models SET image_slug = 'bmw-i3'              WHERE brand = 'BMW'           AND name = 'i3';
UPDATE vehicle_models SET image_slug = 'byd-dolphin'         WHERE brand = 'BYD'           AND name = 'Dolphin';
UPDATE vehicle_models SET image_slug = 'byd-yuan-plus'       WHERE brand = 'BYD'           AND name = 'Yuan Plus';
UPDATE vehicle_models SET image_slug = 'chevrolet-bolt-euv'  WHERE brand = 'Chevrolet'     AND name = 'Bolt EUV';
UPDATE vehicle_models SET image_slug = 'fiat-500e'           WHERE brand = 'Fiat'          AND name = '500e';
UPDATE vehicle_models SET image_slug = 'hyundai-ioniq-5'     WHERE brand = 'Hyundai'       AND name = 'Ioniq 5';
UPDATE vehicle_models SET image_slug = 'hyundai-kona'        WHERE brand = 'Hyundai'       AND name = 'Kona Eléctrico';
UPDATE vehicle_models SET image_slug = 'kia-ev6'             WHERE brand = 'Kia'           AND name = 'EV6';
UPDATE vehicle_models SET image_slug = 'mercedes-benz-eqb'   WHERE brand = 'Mercedes-Benz' AND name = 'EQB';
UPDATE vehicle_models SET image_slug = 'nissan-leaf'         WHERE brand = 'Nissan'        AND name = 'Leaf';
UPDATE vehicle_models SET image_slug = 'nissan-leaf-e-plus'  WHERE brand = 'Nissan'        AND name = 'Leaf e+';
UPDATE vehicle_models SET image_slug = 'peugeot-e-208'       WHERE brand = 'Peugeot'       AND name = 'e-208';
UPDATE vehicle_models SET image_slug = 'renault-kangoo'      WHERE brand = 'Renault'       AND name = 'Kangoo E-Tech';
UPDATE vehicle_models SET image_slug = 'renault-zoe'         WHERE brand = 'Renault'       AND name = 'Zoe';
UPDATE vehicle_models SET image_slug = 'tesla-model-3'       WHERE brand = 'Tesla'         AND name = 'Model 3';
UPDATE vehicle_models SET image_slug = 'tesla-model-y'       WHERE brand = 'Tesla'         AND name = 'Model Y';
UPDATE vehicle_models SET image_slug = 'toyota-bz4x'         WHERE brand = 'Toyota'        AND name = 'bZ4X';
UPDATE vehicle_models SET image_slug = 'volkswagen-id-4'     WHERE brand = 'Volkswagen'    AND name = 'ID.4';
UPDATE vehicle_models SET image_slug = 'volvo-ex30'          WHERE brand = 'Volvo'         AND name = 'EX30';

-- Recién obligatoria DESPUÉS de llenar las que había: puesta antes, el ALTER falla contra
-- cualquier base que ya tenga filas.
--
-- **Y es obligatoria a propósito, aunque el archivo pueda no existir todavía.** Son dos cosas
-- distintas: que el modelo declare CÓMO se llama su foto es un dato del catálogo y nunca falta;
-- que el archivo esté subido es un estado del frontend, y de eso se ocupa el dibujo de reserva
-- que se muestra cuando la imagen no carga. Dejándola nullable, un modelo cargado a las apuradas
-- entra sin nombre de foto y nadie se entera hasta que alguien lo elige.
ALTER TABLE vehicle_models ALTER COLUMN image_slug SET NOT NULL;
