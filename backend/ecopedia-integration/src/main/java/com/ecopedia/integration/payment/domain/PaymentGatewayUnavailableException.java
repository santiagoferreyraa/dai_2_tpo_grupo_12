package com.ecopedia.integration.payment.domain;

/**
 * La pasarela no pudo contestar: está caída, tardó demasiado o rechazó nuestras credenciales.
 *
 * <p>Es distinto de una tarjeta rechazada, y por eso no es un {@link IllegalArgumentException}. Una
 * tarjeta rechazada es un dato que el conductor puede corregir; esto no: lo que corresponde es
 * decirle que pruebe más tarde, y en la capa web se traduce a un 503.
 */
public class PaymentGatewayUnavailableException extends RuntimeException {

    public PaymentGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
