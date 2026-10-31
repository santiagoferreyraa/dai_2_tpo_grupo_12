package com.ecopedia.charging.checkout.domain;

/** El checkout llegó sin {@code Idempotency-Key}, o con una que no tiene la forma esperada. */
public class IdempotencyKeyRequiredException extends RuntimeException {

    public IdempotencyKeyRequiredException() {
        super("El checkout necesita la cabecera Idempotency-Key: de 8 a 100 letras, números, guiones o guiones bajos");
    }
}
