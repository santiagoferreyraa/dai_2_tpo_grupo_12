package com.ecopedia.integration.payment.domain;

/**
 * La tarjeta no existe, está dada de baja o es de otro conductor: desde afuera, las tres cosas
 * son la misma (SEC-06). La API la contesta con el 404 de cualquier ruta inexistente.
 */
public class PaymentMethodNotFoundException extends RuntimeException {

    public PaymentMethodNotFoundException(Long cardId) {
        super("No se encontró la tarjeta con ID: " + cardId);
    }
}
