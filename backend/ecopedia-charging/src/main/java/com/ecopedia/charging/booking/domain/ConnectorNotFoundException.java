package com.ecopedia.charging.booking.domain;

/** El conector no existe en Terminales. Se responde 404. */
public class ConnectorNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConnectorNotFoundException(Long connectorId) {
        super("El conector " + connectorId + " no existe");
    }
}
