package com.ecopedia.charging.session.domain;

/**
 * El conductor ya tiene una sesión de carga activa y quiso iniciar otra (RF11, ECO-44). Un auto, un
 * conector: la segunda carga tendría que esperar a que termine la primera.
 */
public class DriverAlreadyChargingException extends RuntimeException {

    private final String activeSessionId;

    public DriverAlreadyChargingException(Long driverId, String activeSessionId) {
        super("El conductor " + driverId + " ya tiene la sesión " + activeSessionId + " activa");
        this.activeSessionId = activeSessionId;
    }

    public String getActiveSessionId() {
        return activeSessionId;
    }
}
