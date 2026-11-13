package com.ecopedia.integration.payment.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Petición interna para cobrar el consumo final de una sesión de carga (ECO-55).
 */
public record ConsumptionChargeBody(
        @NotNull(message = "driverId es obligatorio") Long driverId,
        Long paymentMethodId,
        @NotNull(message = "amount es obligatorio") @Positive(message = "amount debe ser positivo") BigDecimal amount,
        @NotBlank(message = "sessionId es obligatorio") String sessionId,
        @NotBlank(message = "idempotencyKey es obligatorio") String idempotencyKey) {}
