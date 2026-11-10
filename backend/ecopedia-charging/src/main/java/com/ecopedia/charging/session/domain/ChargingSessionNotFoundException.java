package com.ecopedia.charging.session.domain;

/**
 * No hay una sesión con ese id, o es de otro conductor. Hacia afuera es el mismo 404 en los dos casos
 * (QA-31): confirmar que una sesión ajena existe ya es contar algo.
 */
public class ChargingSessionNotFoundException extends RuntimeException {

    public ChargingSessionNotFoundException(String sessionId) {
        super("Sesión de carga no encontrada: " + sessionId);
    }
}
