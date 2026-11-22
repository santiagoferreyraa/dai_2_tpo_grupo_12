package com.ecopedia.charging.booking.domain;

/**
 * El conector existe pero no se puede reservar: está fuera de servicio. Se responde 409.
 *
 * <p>Es la misma regla que el front ya aplica deshabilitando el botón de reservar; acá es la que
 * vale, porque el front se puede saltear.
 */
public class ConnectorNotBookableException extends RuntimeException {

    public ConnectorNotBookableException(Long connectorId) {
        super("El conector " + connectorId + " está fuera de servicio y no se puede reservar");
    }

    public ConnectorNotBookableException(Long connectorId, String message) {
        super(message);
    }
}
