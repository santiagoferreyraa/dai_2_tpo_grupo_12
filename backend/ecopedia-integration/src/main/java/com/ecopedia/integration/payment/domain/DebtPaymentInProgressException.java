package com.ecopedia.integration.payment.domain;

/**
 * Otro cobro de la misma deuda está en curso —el reintento del barrido, o el mismo conductor desde
 * otra pestaña—. Se rechaza en vez de esperar, para que no salgan dos cobros (ECO-62).
 */
public class DebtPaymentInProgressException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DebtPaymentInProgressException(Long debtId) {
        super("Ya hay un cobro en curso de la deuda " + debtId);
    }
}
