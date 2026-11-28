package com.ecopedia.integration.payment.domain;

public class DepositChargeNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DepositChargeNotFoundException(Long chargeId) {
        super("No existe el cobro de seña " + chargeId);
    }
}
