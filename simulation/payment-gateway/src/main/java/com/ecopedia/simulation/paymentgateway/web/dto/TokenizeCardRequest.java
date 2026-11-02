package com.ecopedia.simulation.paymentgateway.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** {@code POST /v1/card_tokens}. Los nombres siguen los de Mercado Pago, en snake_case. */
public record TokenizeCardRequest(
        @NotBlank String cardNumber,
        @NotNull Integer expirationMonth,
        @NotNull Integer expirationYear,
        @NotNull @Valid Cardholder cardholder) {

    public record Cardholder(@NotBlank String name) {}

    /** El número no aparece en ningún log, ni en el de un error de validación. */
    @Override
    public String toString() {
        return "TokenizeCardRequest[cardNumber=****]";
    }
}
