package com.ecopedia.async.notification.domain;

import java.time.Instant;

/**
 * Los datos de un aviso de deuda (ECO-62): de qué carga es, en qué reintento va y cuándo es el
 * próximo. Sin el monto: está en Medios de pago, y los avisos no lo repiten (Manuel, 31/10).
 *
 * @param incurredAt cuándo se cerró la carga que no se pudo cobrar
 * @param attempt el reintento que falló; nulo si el aviso no es de un reintento
 * @param maxAttempts cuántos reintentos hay en total; nulo si no hace falta decirlo
 * @param occurredAt cuándo pasó el hecho
 * @param nextAttemptAt cuándo es el próximo reintento; nulo si no hay más
 */
public record DebtFacts(
        Instant incurredAt, Integer attempt, Integer maxAttempts, Instant occurredAt, Instant nextAttemptAt) {}
