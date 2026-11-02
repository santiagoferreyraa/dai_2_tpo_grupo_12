package com.ecopedia.simulation.paymentgateway.domain;

import org.springframework.http.HttpStatus;

/**
 * Un pedido que la pasarela rechaza, con el estado HTTP y un {@code code} estable.
 *
 * <p>El {@code code} es el contrato: Pagos decide qué le dice al conductor según el código, no
 * según el texto. El mensaje va en inglés, como el de cualquier API de un tercero, y nunca llega
 * tal cual a la pantalla (directriz 7).
 */
public class GatewayException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public GatewayException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static GatewayException badRequest(String code, String message) {
        return new GatewayException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static GatewayException notFound(String code, String message) {
        return new GatewayException(HttpStatus.NOT_FOUND, code, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
