package com.ecopedia.integration.payment.domain;

/**
 * La deuda no existe o es de otro conductor. Las dos cosas se contestan igual, con el 404 único: un
 * mensaje distinto para "es de otro" confirmaría que ese id existe (SEC-06). El motivo va al log.
 */
public class DebtNotFoundException extends RuntimeException {

    public DebtNotFoundException(Long debtId) {
        super("Deuda " + debtId + " no encontrada");
    }
}
