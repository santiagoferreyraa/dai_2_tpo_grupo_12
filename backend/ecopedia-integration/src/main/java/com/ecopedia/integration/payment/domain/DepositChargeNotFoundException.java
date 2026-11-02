package com.ecopedia.integration.payment.domain;

public class DepositChargeNotFoundException extends RuntimeException {

    public DepositChargeNotFoundException(Long chargeId) {
        super("No existe el cobro de seña " + chargeId);
    }
}
