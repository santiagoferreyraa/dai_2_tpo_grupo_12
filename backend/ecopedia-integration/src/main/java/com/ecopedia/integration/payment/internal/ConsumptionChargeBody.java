package com.ecopedia.integration.payment.internal;

import com.ecopedia.integration.payment.domain.ConsumptionChargeRequest;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * El cobro del consumo que pide SesionesDeCarga al cerrar una sesión (ECO-44).
 *
 * <p>La referencia sale con la forma de una clave de idempotencia porque lo es: Pagos la usa como
 * base de la clave ante la pasarela.
 */
public record ConsumptionChargeBody(
        @NotNull Long driverId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Size(max = 120) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String reference) {

    ConsumptionChargeRequest toDomain() {
        return new ConsumptionChargeRequest(driverId, amount, reference);
    }
}
