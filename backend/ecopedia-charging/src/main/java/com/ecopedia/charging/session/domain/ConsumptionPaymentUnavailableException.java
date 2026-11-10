package com.ecopedia.charging.session.domain;

/** Pagos no contestó al cobrar el consumo de una sesión. */
public class ConsumptionPaymentUnavailableException extends RuntimeException {

    public ConsumptionPaymentUnavailableException(Throwable cause) {
        super("No se pudo cobrar el consumo de la sesión", cause);
    }
}
