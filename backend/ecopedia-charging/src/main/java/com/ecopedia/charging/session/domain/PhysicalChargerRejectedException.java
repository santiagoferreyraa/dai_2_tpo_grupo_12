package com.ecopedia.charging.session.domain;

/**
 * El cargador físico rechazó la orden de encendido (ej. conector ocupado en hardware o averiado).
 */
public class PhysicalChargerRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PhysicalChargerRejectedException(String message) {
        super(message);
    }

    public PhysicalChargerRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
