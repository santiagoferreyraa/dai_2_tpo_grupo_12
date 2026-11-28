package com.ecopedia.charging.checkout.domain;

/**
 * La tarjeta elegida no sirve para cobrar: no existe, no es del conductor, está dada de baja o
 * vencida. Pagos no distingue, a propósito (SEC-06).
 */
public class PaymentMethodInvalidException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PaymentMethodInvalidException() {
        super("La tarjeta elegida no se puede usar para cobrar la seña");
    }

    public PaymentMethodInvalidException(String message) {
        super(message);
    }

    public PaymentMethodInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
