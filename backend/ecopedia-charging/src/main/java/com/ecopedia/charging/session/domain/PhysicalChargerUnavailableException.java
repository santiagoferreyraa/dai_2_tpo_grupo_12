package com.ecopedia.charging.session.domain;

/**
 * El cargador físico no respondió o no está disponible en la red (RNF06, RF13, ECO-60).
 */
public class PhysicalChargerUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PhysicalChargerUnavailableException(String message) {
        super(message);
    }

    public PhysicalChargerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
