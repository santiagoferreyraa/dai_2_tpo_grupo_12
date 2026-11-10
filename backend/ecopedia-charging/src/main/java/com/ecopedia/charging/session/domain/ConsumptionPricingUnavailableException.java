package com.ecopedia.charging.session.domain;

/** Tarificación no contestó al calcular el importe de una sesión. */
public class ConsumptionPricingUnavailableException extends RuntimeException {

    public ConsumptionPricingUnavailableException(Throwable cause) {
        super("No se pudo calcular el importe del consumo", cause);
    }
}
