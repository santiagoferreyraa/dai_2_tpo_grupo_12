package com.ecopedia.simulator.pasarela.domain;

import org.springframework.http.HttpStatus;

/**
 * Un rechazo de la pasarela, con el código que ve el comercio.
 *
 * <p>Los códigos son de la pasarela, no de Ecopedia, y a propósito están en su idioma
 * ({@code incorrect_number}, {@code cc_rejected_insufficient_amount}, ...). Convertirlos en un
 * mensaje que un conductor entienda es trabajo del Adapter del otro lado.
 *
 * @param status código HTTP con el que se contesta.
 * @param type familia del error: {@code card_error}, {@code invalid_request_error},
 *     {@code authentication_error}.
 * @param code motivo puntual, estable, pensado para que el comercio lo interprete por programa.
 */
public class GatewayException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String code;

    public GatewayException(HttpStatus status, String type, String code, String message) {
        super(message);
        this.status = status;
        this.type = type;
        this.code = code;
    }

    /** Tarjeta rechazada: 402, como en las pasarelas reales. */
    public static GatewayException cardError(String code, String message) {
        return new GatewayException(HttpStatus.PAYMENT_REQUIRED, "card_error", code, message);
    }

    /** El pedido está mal armado: es un error del comercio, no del tarjetahabiente. */
    public static GatewayException invalidRequest(String code, String message) {
        return new GatewayException(HttpStatus.BAD_REQUEST, "invalid_request_error", code, message);
    }

    public static GatewayException notFound(String message) {
        return new GatewayException(HttpStatus.NOT_FOUND, "invalid_request_error", "resource_not_found", message);
    }

    public HttpStatus status() {
        return status;
    }

    public String type() {
        return type;
    }

    public String code() {
        return code;
    }
}
