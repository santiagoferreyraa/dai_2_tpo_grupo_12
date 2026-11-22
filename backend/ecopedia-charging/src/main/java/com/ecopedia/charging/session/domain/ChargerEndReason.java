package com.ecopedia.charging.session.domain;

/** Por qué el cargador dio por terminada una sesión. Viene en su última lectura. */
public enum ChargerEndReason {
    /** Ecopedia lo apagó: el conductor detuvo la carga o venció la ventana. */
    STOPPED,
    /** La batería se llenó: el cargador cortó solo. */
    BATTERY_FULL,
    /** El cargador falló con la carga en curso. */
    FAULT
}
