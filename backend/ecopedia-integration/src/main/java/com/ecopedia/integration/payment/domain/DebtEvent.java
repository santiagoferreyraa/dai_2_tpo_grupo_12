package com.ecopedia.integration.payment.domain;

import java.time.Instant;

/**
 * Un hecho de una deuda, tal como viaja a Notificaciones por la cola {@code notifications.dispatch}
 * (ECO-62).
 *
 * <p>Trae datos y no texto: las palabras las pone Notificaciones. <b>Sin el monto</b>, como los avisos
 * de la seña (decidido por Manuel el 31/10): el monto está en Medios de pago.
 *
 * <p><b>El {@code eventId} sale del hecho, no del azar</b> ({@code debt-<id>-retry-2-failed}): si el mismo
 * hecho se publica dos veces —un reenvío, un barrido repetido—, Notificaciones lo reconoce y avisa una
 * sola vez.
 *
 * @param incurredAt cuándo se cerró la carga que no se pudo cobrar: "la carga del 22/11"
 * @param attempt el reintento que falló; nulo si el hecho no es un reintento
 * @param maxAttempts cuántos reintentos hay en total
 * @param nextAttemptAt cuándo es el próximo reintento; nulo si no hay más
 */
public record DebtEvent(
        String eventId,
        DebtEventType type,
        Long debtId,
        Long driverId,
        Instant incurredAt,
        Integer attempt,
        Integer maxAttempts,
        Instant occurredAt,
        Instant nextAttemptAt) {

    /** El cobro al cerrar la carga falló. */
    public static DebtEvent recorded(PendingPayment debt, Instant now) {
        return new DebtEvent(
                "debt-" + debt.getId() + "-recorded",
                DebtEventType.DEBT_RECORDED,
                debt.getId(),
                debt.getDriverId(),
                debt.getCreatedAt(),
                null,
                null,
                now,
                debt.getNextAttemptAt());
    }

    /**
     * Un reintento que falló, ya registrado en la deuda. Si fue el último, es la marca de la cuenta:
     * un solo aviso para las dos cosas (Manuel, 22/11).
     */
    public static DebtEvent retryFailed(PendingPayment debt, int maxAttempts, Instant now) {
        boolean last = debt.isDefaulted();
        return new DebtEvent(
                "debt-" + debt.getId() + (last ? "-defaulted" : "-retry-" + debt.getRetryCount() + "-failed"),
                last ? DebtEventType.DEBT_DEFAULTED : DebtEventType.DEBT_RETRY_FAILED,
                debt.getId(),
                debt.getDriverId(),
                debt.getCreatedAt(),
                debt.getRetryCount(),
                maxAttempts,
                now,
                debt.getNextAttemptAt());
    }

    /** Se cobró. */
    public static DebtEvent settled(PendingPayment debt, Instant now) {
        return new DebtEvent(
                "debt-" + debt.getId() + "-settled",
                DebtEventType.DEBT_SETTLED,
                debt.getId(),
                debt.getDriverId(),
                debt.getCreatedAt(),
                null,
                null,
                now,
                null);
    }
}
