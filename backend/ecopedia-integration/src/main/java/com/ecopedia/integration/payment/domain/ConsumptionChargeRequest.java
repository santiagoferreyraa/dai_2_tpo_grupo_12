package com.ecopedia.integration.payment.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Pedido de cobro del consumo final (saldo posterior a la seña) de una sesión cerrada (RF14, ECO-55).
 *
 * @param driverId identificador del conductor
 * @param paymentMethodId identificador opcional de la tarjeta; si es {@code null} se usa la activa más reciente
 * @param amount importe positivo a cobrar (diferencia sobre la seña o total si fue walk-in)
 * @param sessionId identificador único de la sesión de carga
 * @param idempotencyKey clave de idempotencia del cobro ante la pasarela
 */
public record ConsumptionChargeRequest(
        Long driverId, Long paymentMethodId, BigDecimal amount, String sessionId, String idempotencyKey) {

    public ConsumptionChargeRequest {
        Objects.requireNonNull(driverId, "driverId no puede ser null");
        Objects.requireNonNull(amount, "amount no puede ser null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("El importe a cobrar debe ser positivo");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId es obligatorio");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey es obligatorio");
        }
    }
}
