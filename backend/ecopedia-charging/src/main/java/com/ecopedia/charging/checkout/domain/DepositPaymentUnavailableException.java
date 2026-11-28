package com.ecopedia.charging.checkout.domain;

/**
 * Pagos o la pasarela no contestaron al cobrar o devolver la seña. No se sabe si cobró: el
 * reintento con la misma clave lo averigua sin cobrar dos veces.
 */
public class DepositPaymentUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DepositPaymentUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
