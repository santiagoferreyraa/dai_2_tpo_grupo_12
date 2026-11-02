package com.ecopedia.simulation.paymentgateway.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** {@code POST /v1/payments}: cobrarle {@code transaction_amount} a la tarjeta del {@code token}. */
public record CreatePaymentRequest(
        @NotNull BigDecimal transactionAmount, @NotBlank String token, String description, String externalReference) {}
