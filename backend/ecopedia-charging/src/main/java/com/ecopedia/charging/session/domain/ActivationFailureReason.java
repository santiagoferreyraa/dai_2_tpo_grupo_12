package com.ecopedia.charging.session.domain;

/**
 *Las tres causas ajenas al conductor por las que una carga con reserva no puede iniciarse
 * (RF13, ECO-59), y que corresponden al único caso del sistema que reintegra la seña una vez
 * llegada la ventana.
 */
public enum ActivationFailureReason {
    /** El conector pasó a estar fuera de servicio después de confirmada la reserva. */
    CONNECTOR_OUT_OF_SERVICE,
    /** El cargador físico no responde o rechaza la orden de encendido. */
    CHARGER_UNAVAILABLE,
    /** La distribuidora eléctrica rechazó el pedido con tope de potencia 0 (RF15). */
    REJECTED_BY_GRID
}
