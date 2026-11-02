package com.ecopedia.charging.session.domain;

/**
 * No hubo respuesta: la distribuidora no contestó a tiempo, o RedEléctrica (ecopedia-integration) no
 * está, o rechazó la clave de servicio. Para la regla de potencia es lo mismo; el log dice cuál fue.
 */
public class GridUnavailableException extends RuntimeException {

    public GridUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
