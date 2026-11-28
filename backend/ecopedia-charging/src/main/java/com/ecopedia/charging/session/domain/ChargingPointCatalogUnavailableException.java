package com.ecopedia.charging.session.domain;

/** Terminales (ecopedia-core) no contestó. El mensaje es para el log, no para la pantalla. */
public class ChargingPointCatalogUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ChargingPointCatalogUnavailableException(Throwable cause) {
        super("No se pudo consultar el conector a ecopedia-core", cause);
    }
}
