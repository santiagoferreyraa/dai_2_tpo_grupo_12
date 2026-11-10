package com.ecopedia.charging.session.domain;

/**
 * Por qué terminó una sesión de carga.
 *
 * <p>ECO-44 escribe {@link #DRIVER_STOPPED}. Los otros los escribe el corte automático (ECO-45, RF12)
 * y la telemetría del cargador, y están desde ahora para que la columna no cambie de forma cuando
 * lleguen.
 */
public enum SessionEndReason {
    /** El conductor la detuvo desde la aplicación (RF11). */
    DRIVER_STOPPED,
    /** El vehículo llegó al porcentaje objetivo (RF12). */
    TARGET_REACHED,
    /** Se agotó la ventana reservada o calculada (RF12). */
    WINDOW_ENDED,
    /** El cargador avisó que la batería se llenó. */
    BATTERY_FULL,
    /** El cargador falló con la carga en curso (RF13). */
    FAULT
}
