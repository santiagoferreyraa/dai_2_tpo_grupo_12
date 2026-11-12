package com.ecopedia.charging.session.messaging;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Contrato del evento de telemetría de cargadores recibido por el tópico {@code telemetry.charging} (RF11, RF16, ECO-45, ECO-55).
 *
 * <p>Mapeado al identificador lógico {@code "telemetry-event"} en el conversor de mensajes JMS.
 */
public record ChargingTelemetryMessage(
        String eventId,
        Type type,
        String sessionId,
        long connectorId,
        Instant timestamp,
        BigDecimal powerKw,
        BigDecimal energyKwh,
        BigDecimal batteryPercent,
        EndReason endReason) {

    public enum Type {
        READING,
        FINISHED,
        FAULT
    }

    public enum EndReason {
        STOPPED,
        BATTERY_FULL,
        FAULT
    }
}
