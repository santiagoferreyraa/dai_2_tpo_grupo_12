package com.ecopedia.core.shared;

/**
 * La versión que mandó el cliente ya no es la guardada: alguien más cambió el recurso desde que lo
 * leyó (QA-30). La API lo contesta 412 con {@code code} {@value #CODE}, y el front pide recargar.
 *
 * <p>No extiende {@link IllegalArgumentException}, a diferencia de {@link ResourceNotFoundException}:
 * el pedido está bien formado, y el manejador de esa excepción lo convertiría en un 400.
 */
public class StaleVersionException extends RuntimeException {

    public static final String CODE = "STALE_VERSION";

    private static final long serialVersionUID = 1L;

    public StaleVersionException(String message) {
        super(message);
    }

    public StaleVersionException(String message, Throwable cause) {
        super(message, cause);
    }
}
