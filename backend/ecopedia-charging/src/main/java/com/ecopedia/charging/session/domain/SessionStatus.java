package com.ecopedia.charging.session.domain;

/**
 * Estado del ciclo de vida de una sesión de carga (RF11, RF12, RF13, ECO-60).
 */
public enum SessionStatus {
    /** Sesión iniciada y activa en el conector. */
    ACTIVE,
    /** Sesión finalizada con normalidad (corte por porcentaje objetivo o fin de ventana). */
    COMPLETED,
    /** Sesión interrumpida por una falla física o del sistema. */
    FAULTED
}
