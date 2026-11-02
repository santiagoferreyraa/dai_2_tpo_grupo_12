package com.ecopedia.charging.session.domain;

/**
 * La distribuidora contestó con un Fault: el pedido no se pudo procesar.
 *
 * @see #getCode() {@code UNKNOWN_NODE}, {@code INVALID_DEMAND} o {@code INVALID_REQUEST}
 */
public class GridRequestRejectedException extends RuntimeException {

    public static final String UNKNOWN_NODE = "UNKNOWN_NODE";

    private final String code;

    public GridRequestRejectedException(String code) {
        super("La distribuidora rechazó el pedido: " + code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
