package com.ecopedia.integration.payment.internal;

import jakarta.validation.constraints.NotNull;

/**
 * Petición interna de validación de medio de pago y ausencia de deuda (ECO-54).
 */
public record PaymentValidationRequestBody(
        @NotNull(message = "driverId es obligatorio") Long driverId, Long paymentMethodId) {}
