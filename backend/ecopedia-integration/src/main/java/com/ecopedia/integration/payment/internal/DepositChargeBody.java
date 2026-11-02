package com.ecopedia.integration.payment.internal;

import com.ecopedia.integration.payment.domain.DepositChargeRequest;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** El pedido de cobro de seña que manda el checkout desde ecopedia-charging. */
public record DepositChargeBody(
        @NotNull Long driverId,
        Long paymentMethodId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Size(max = 120) String reference,
        @NotBlank @Size(max = 160) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String idempotencyKey) {

    DepositChargeRequest toDomain() {
        return new DepositChargeRequest(driverId, paymentMethodId, amount, reference, idempotencyKey);
    }
}
