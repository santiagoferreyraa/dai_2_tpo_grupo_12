package com.ecopedia.integration.payment.domain;

/** La pasarela rechazó el cobro con el que el conductor quiso saldar su deuda (ECO-62). */
public class DebtPaymentDeclinedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DebtPaymentDeclinedException(Long debtId, String statusDetail) {
        super("La pasarela rechazó el pago de la deuda " + debtId + ": " + statusDetail);
    }
}
