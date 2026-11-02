package com.ecopedia.charging.checkout.domain;

/** La clave ya confirmó otra retención: es un pedido distinto que reusa la clave, no un reintento. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("Esa Idempotency-Key ya se usó para confirmar otra retención");
    }
}
