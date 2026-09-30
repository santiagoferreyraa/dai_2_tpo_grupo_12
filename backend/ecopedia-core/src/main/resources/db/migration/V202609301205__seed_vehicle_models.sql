-- El catálogo de modelos con el que arranca el sistema.
--
-- Va como migración de Flyway y no como data.sql por lo mismo que el seed de estaciones: es el
-- mismo mecanismo que el resto del esquema, así que todos los que levantan el proyecto ven
-- exactamente las mismas filas sin pasos extra.
--
-- **Las fichas son reales.** Si los números fueran inventados, la estimación de tiempo de carga
-- de la portada y el conteo de estaciones compatibles darían resultados sin sentido, y no habría
-- forma de distinguir un bug de la cuenta de un dato malo. battery_kwh es capacidad UTILIZABLE,
-- que es la que importa para estimar una carga; max_charge_kw es el pico en corriente continua.
--
-- **La selección cubre los tres conectores a propósito.** Si fueran todos CCS2, CompatibilityCard
-- diría siempre "15 de 15" y el filtro por conector del mapa no tendría contra qué probarse.
-- Los dos Leaf traen CHAdeMO y el Zoe, Tipo 2 —que además es el único que carga lento, así que
-- es el caso donde la estimación de tiempo se nota—.
--
-- Sin ids explícitos: a diferencia del seed de estaciones, nada referencia estas filas dentro de
-- la misma migración, así que no hace falta fijarlos ni reposicionar la secuencia después.
INSERT INTO vehicle_models (brand, name, connector_type, motor_kw, max_charge_kw, battery_kwh) VALUES
    ('BMW',           'i3',            'CCS2',    125.00,  50.00, 37.90),
    ('BYD',           'Dolphin',       'CCS2',    150.00,  60.00, 44.90),
    ('BYD',           'Yuan Plus',     'CCS2',    150.00,  88.00, 60.50),
    ('Chevrolet',     'Bolt EUV',      'CCS2',    150.00,  55.00, 65.00),
    ('Fiat',          '500e',          'CCS2',     87.00,  85.00, 37.30),
    ('Hyundai',       'Ioniq 5',       'CCS2',    168.00, 235.00, 77.40),
    ('Hyundai',       'Kona Eléctrico','CCS2',    150.00,  77.00, 64.00),
    ('Kia',           'EV6',           'CCS2',    168.00, 240.00, 77.40),
    ('Mercedes-Benz', 'EQB',           'CCS2',    168.00, 100.00, 66.50),
    ('Nissan',        'Leaf',          'CHADEMO', 110.00,  50.00, 39.00),
    ('Nissan',        'Leaf e+',       'CHADEMO', 160.00, 100.00, 59.00),
    ('Peugeot',       'e-208',         'CCS2',    100.00, 100.00, 48.10),
    ('Renault',       'Kangoo E-Tech', 'CCS2',     90.00,  80.00, 45.00),
    ('Renault',       'Zoe',           'TYPE_2',  100.00,  22.00, 52.00),
    ('Tesla',         'Model 3',       'CCS2',    208.00, 170.00, 57.50),
    ('Tesla',         'Model Y',       'CCS2',    220.00, 170.00, 60.00),
    ('Toyota',        'bZ4X',          'CCS2',    150.00, 150.00, 64.00),
    ('Volkswagen',    'ID.4',          'CCS2',    150.00, 135.00, 77.00),
    ('Volvo',         'EX30',          'CCS2',    200.00, 153.00, 64.00);
