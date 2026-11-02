package com.ecopedia.integration.payment.domain;

/** El cobro no se puede devolver: la pasarela lo rechazó, así que no salió plata. */
public class DepositNotRefundableException extends RuntimeException {

    public DepositNotRefundableException(Long chargeId) {
        super("El cobro de seña " + chargeId + " fue rechazado: no hay nada que devolver");
    }
}
