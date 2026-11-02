package com.ecopedia.simulation.charger.domain;

/** En qué está un conector, visto desde el hardware. */
public enum ConnectorStatus {
    AVAILABLE,
    CHARGING,
    /** Roto a propósito, para la demo. No se activa hasta que se lo repare. */
    FAULTED
}
