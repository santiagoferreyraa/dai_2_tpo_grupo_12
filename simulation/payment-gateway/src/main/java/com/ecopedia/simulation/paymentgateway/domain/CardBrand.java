package com.ecopedia.simulation.paymentgateway.domain;

/**
 * Las marcas que la pasarela sabe cobrar, con el identificador que usa su API.
 *
 * <p>{@code visa}, {@code master} y {@code amex} son los {@code payment_method_id} de Mercado
 * Pago: la API de la pasarela imita esa forma a propósito.
 */
public enum CardBrand {
    VISA("visa"),
    MASTERCARD("master"),
    AMEX("amex");

    private final String apiId;

    CardBrand(String apiId) {
        this.apiId = apiId;
    }

    public String apiId() {
        return apiId;
    }
}
