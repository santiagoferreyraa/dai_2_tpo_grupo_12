package com.ecopedia.charging.checkout.domain;

/**
 * Excepción lanzada cuando el conductor intenta realizar un checkout sin contar con un medio de pago registrado (RF02).
 */
public class PaymentMethodRequiredException extends RuntimeException {

    public PaymentMethodRequiredException(String message) {
        super(message);
    }
}
