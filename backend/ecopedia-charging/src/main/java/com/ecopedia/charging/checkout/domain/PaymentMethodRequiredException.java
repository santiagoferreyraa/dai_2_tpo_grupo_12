package com.ecopedia.charging.checkout.domain;

/**
 * Excepción lanzada cuando el conductor intenta realizar un checkout sin contar con un medio de pago registrado (RF02).
 */
public class PaymentMethodRequiredException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PaymentMethodRequiredException(String message) {
        super(message);
    }
}
