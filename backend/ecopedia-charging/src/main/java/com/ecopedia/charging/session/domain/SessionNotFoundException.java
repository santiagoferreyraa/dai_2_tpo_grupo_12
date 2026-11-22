package com.ecopedia.charging.session.domain;

/**
 * La sesión de carga no existe o pertenece a otro conductor (SEC-06).
 */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(String sessionId) {
        super("La sesión " + sessionId + " no existe");
    }
}
