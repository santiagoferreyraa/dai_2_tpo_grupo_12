package com.ecopedia.integration.payment.domain;

/**
 * La pasarela no contestó: caída, más lenta que el timeout, o rechazó la clave de acceso.
 *
 * <p>Se separa de un rechazo porque quien llama tiene que hacer otra cosa: ante un "no" se le
 * explica al conductor qué pasó; ante esto se le pide que pruebe en un rato, o se reintenta. El
 * mensaje está escrito para el conductor, porque es lo que termina viendo (directriz 7).
 */
public class PaymentGatewayUnavailableException extends RuntimeException {

    public PaymentGatewayUnavailableException(Throwable cause) {
        super("No pudimos comunicarnos con el procesador de pagos. Probá de nuevo en un momento.", cause);
    }
}
