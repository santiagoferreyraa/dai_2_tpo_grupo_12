package com.ecopedia.charging.session.domain;

/**
 * La sesión de carga no existe o pertenece a otro conductor (SEC-06).
 */
public class SessionNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SessionNotFoundException(String sessionId) {
        super("La sesión " + sessionId + " no existe");
    }
}
