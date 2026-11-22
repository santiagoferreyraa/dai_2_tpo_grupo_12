package com.ecopedia.integration.payment.web.dto;

import com.ecopedia.integration.payment.domain.PendingPayment;
import com.ecopedia.integration.payment.domain.PendingPaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una deuda del conductor, tal como la ve él (ECO-62).
 *
 * <p>No lleva el motivo del rechazo: es el texto de la pasarela, escrito para el log, y la pantalla
 * pone sus propias palabras (directiva 6). Tampoco la sesión ni la tarjeta del intento.
 *
 * @param incurredAt cuándo se cerró la carga que no se pudo cobrar: "Carga del 18/11"
 * @param status {@code PENDING} (se sigue reintentando), {@code DEFAULTED} (cuenta marcada) o
 *     {@code SETTLED} (la respuesta de un pago)
 * @param nextAttemptAt cuándo es el próximo reintento; nulo si ya no hay más
 */
public record DebtResponse(
        Long id, BigDecimal amount, Instant incurredAt, PendingPaymentStatus status, Instant nextAttemptAt) {

    public static DebtResponse fromDomain(PendingPayment debt) {
        return new DebtResponse(
                debt.getId(),
                debt.getAmount(),
                debt.getCreatedAt(),
                debt.getStatus(),
                debt.isPending() ? debt.getNextAttemptAt() : null);
    }
}
