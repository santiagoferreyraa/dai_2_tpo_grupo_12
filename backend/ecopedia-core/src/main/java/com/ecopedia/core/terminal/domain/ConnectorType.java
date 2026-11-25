package com.ecopedia.core.terminal.domain;

import java.math.BigDecimal;

/**
 * Estándar físico del conector, que es lo que determina con qué vehículos es compatible.
 *
 * <p>Son los tres que nombra RF05. Se guardan como texto en la base, no como ordinal:
 * agregar un estándar nuevo no debe correr el significado de los que ya están guardados.
 *
 * <p><b>La potencia máxima de cada uno sale de su norma</b> (QA-25): un conector no puede declarar
 * más de lo que su estándar permite entregar.
 *
 * <ul>
 *   <li><b>CCS2</b>, carga en continua de IEC 62196-3: hasta 1.000 V y 500 A, o sea 500 kW. Los
 *       cargadores comerciales más rápidos andan entre 350 y 400 kW.
 *   <li><b>CHAdeMO</b> 2.0: hasta 1.000 V y 400 A, 400 kW.
 *   <li><b>Tipo 2</b>, carga en alterna de IEC 62196-2: trifásica de 63 A a 400 V, unos 43 kW.
 * </ul>
 */
public enum ConnectorType {
    CCS2("CCS2", "500"),
    CHADEMO("CHAdeMO", "400"),
    TYPE_2("Tipo 2", "43");

    /** El tope de todos: el de CCS2. Es el que publica el contrato OpenAPI. */
    public static final String HIGHEST_MAX_POWER_KW = "500";

    private final String displayName;
    private final BigDecimal maxPowerKw;

    ConnectorType(String displayName, String maxPowerKw) {
        this.displayName = displayName;
        this.maxPowerKw = new BigDecimal(maxPowerKw);
    }

    /** El nombre como se escribe en un mensaje para el usuario. */
    public String displayName() {
        return displayName;
    }

    /** La potencia más alta que permite la norma del conector, en kW. */
    public BigDecimal maxPowerKw() {
        return maxPowerKw;
    }
}
