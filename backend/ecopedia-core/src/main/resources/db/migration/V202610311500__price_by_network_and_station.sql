-- Tarificación con el modelo nuevo (decidido por Manuel el 31/10).
--
-- Nombre con timestamp, NUNCA V1/V2/V3. Ver README, "Migraciones".
--
-- Antes cada conector tenía su esquema: tarifa, seña, multa y una estrategia elegida a mano. Ahora:
--   * los precios de la carga los fija el ADMIN para toda la red: uno para la carga normal y otro
--     para la rápida, más la multa por minuto de exceso;
--   * la seña y el check de hora pico, el CPO, por estación;
--   * la estrategia (tarifa plana o pico) no se guarda: la elige el sistema según el caso.
--
-- Los esquemas por conector se descartan sin migrarlos: eran datos de prueba, y no hay una forma
-- honesta de convertir una tarifa por conector en una de red.

DROP TABLE pricing_schemes;

-- Una sola fila: los precios valen para todas las estaciones. El CHECK lo hace imposible de romper
-- con un INSERT de más.
CREATE TABLE network_rates (
    id                      INTEGER        PRIMARY KEY CHECK (id = 1),
    normal_kwh_rate         NUMERIC(10, 2) NOT NULL CHECK (normal_kwh_rate > 0),
    fast_kwh_rate           NUMERIC(10, 2) NOT NULL CHECK (fast_kwh_rate > 0),
    excess_penalty_per_min  NUMERIC(10, 2) NOT NULL CHECK (excess_penalty_per_min >= 0),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Precios de arranque, en pesos argentinos, para que el sistema recién instalado cobre algo
-- razonable sin que nadie cargue nada. El ADMIN los cambia desde /admin.
INSERT INTO network_rates (id, normal_kwh_rate, fast_kwh_rate, excess_penalty_per_min)
VALUES (1, 350.00, 550.00, 200.00);

-- La configuración de cada estación. Una estación sin fila no pide seña y cobra hora pico: es lo
-- que lee PricingServiceImpl, así que una estación nueva funciona antes de que su CPO la configure.
CREATE TABLE station_pricing (
    station_id            BIGINT         PRIMARY KEY,
    deposit_amount        NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (deposit_amount >= 0),
    peak_pricing_enabled  BOOLEAN        NOT NULL DEFAULT TRUE,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_station_pricing_station FOREIGN KEY (station_id) REFERENCES stations (id) ON DELETE CASCADE
);

-- Las 15 estaciones del seed: $ 5.000 de seña si tienen algún conector rápido (más de 22 kW) y
-- $ 3.000 si no; hora pico activada en todas. Se siembra con un SELECT y no con ids sueltos, así una
-- base donde alguna ya no existe no rompe la migración.
INSERT INTO station_pricing (station_id, deposit_amount, peak_pricing_enabled)
SELECT s.id,
       CASE WHEN EXISTS (SELECT 1 FROM connectors c WHERE c.station_id = s.id AND c.max_power_kw > 22)
            THEN 5000.00 ELSE 3000.00 END,
       TRUE
FROM stations s
WHERE s.id BETWEEN 1 AND 15;
