package com.ecopedia.integration.payment.domain;

/** La clave de idempotencia ya nombra otro cobro: otro conductor u otro importe. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String idempotencyKey) {
        super("La clave " + idempotencyKey + " ya se usó para otro cobro");
    }
}
