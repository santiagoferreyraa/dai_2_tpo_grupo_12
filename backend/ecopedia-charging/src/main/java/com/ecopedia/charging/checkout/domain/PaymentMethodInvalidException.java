package com.ecopedia.charging.checkout.domain;

/**
 * La tarjeta elegida no sirve para cobrar: no existe, no es del conductor, está dada de baja o
 * vencida. Pagos no distingue, a propósito (SEC-06).
 */
public class PaymentMethodInvalidException extends RuntimeException {

    public PaymentMethodInvalidException() {
        super("La tarjeta elegida no se puede usar para cobrar la seña");
    }
}
