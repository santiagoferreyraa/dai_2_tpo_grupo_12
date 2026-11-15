package com.ecopedia.charging.session.domain;

import com.ecopedia.charging.checkout.domain.ConsumptionPaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Resumen completo del cierre de una sesión de carga y su liquidación económica (Flujo C, RF12, RF14, ECO-55).
 */
public record SessionStopResult(
        String sessionId,
        Long bookingId,
        Long connectorId,
        SessionStatus status,
        Instant startedAt,
        Instant stoppedAt,
        BigDecimal kwhDelivered,
        BigDecimal depositAmount,
        BigDecimal realCost,
        BigDecimal finalBilled,
        BigDecimal additionalCharge,
        ConsumptionPaymentStatus paymentStatus,
        SessionStopReason stopReason) {}
