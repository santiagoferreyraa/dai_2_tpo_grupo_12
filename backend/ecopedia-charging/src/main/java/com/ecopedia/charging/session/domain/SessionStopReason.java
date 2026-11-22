package com.ecopedia.charging.session.domain;

/**
 * Por qué se cerró una sesión de carga (RF11, RF12, ECO-45).
 *
 * <p>No es lo mismo que {@link ChargerEndReason}, que es lo que informa el cargador sobre su propio
 * corte. Esto es quién decidió cerrar la sesión de este lado, y es lo que le muestra la pantalla al
 * conductor cuando la carga terminó sin que él tocara nada.
 */
public enum SessionStopReason {
    /** El conductor la detuvo desde la aplicación (RF11, ECO-44). */
    DRIVER_STOPPED,
    /** La batería llegó al porcentaje que eligió el conductor (RF12, ECO-45). */
    TARGET_REACHED,
    /** Se terminó la ventana: la de la reserva, o la calculada de la carga sin reserva (RF12, ECO-45). */
    WINDOW_ENDED
}
