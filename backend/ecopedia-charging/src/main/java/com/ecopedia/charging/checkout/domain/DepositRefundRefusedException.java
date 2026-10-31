package com.ecopedia.charging.checkout.domain;

/** Pagos contestó que ese cobro no se puede devolver. No se arregla reintentando. */
public class DepositRefundRefusedException extends RuntimeException {

    public DepositRefundRefusedException(String code) {
        super("Pagos no aceptó devolver la seña: " + code);
    }
}
