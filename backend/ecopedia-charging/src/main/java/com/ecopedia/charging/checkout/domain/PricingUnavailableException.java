package com.ecopedia.charging.checkout.domain;

/**
 * Tarificación no contestó y no se sabe cuánto es la seña.
 *
 * <p>Hasta ECO-53 se confirmaba igual con seña 0, y estaba anotado como deuda: con la seña cobrada
 * de verdad, eso regalaba reservas sin seña con solo tener core caído. Ahora no se confirma.
 */
public class PricingUnavailableException extends RuntimeException {

    public PricingUnavailableException(Throwable cause) {
        super("No se pudo calcular la seña: Tarificación no contestó", cause);
    }
}
