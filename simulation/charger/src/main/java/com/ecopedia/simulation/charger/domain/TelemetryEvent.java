package com.ecopedia.simulation.charger.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una lectura del cargador, tal como sale al tópico {@code telemetry.charging}.
 *
 * <p>{@code energyKwh} es <b>acumulado</b> desde que arrancó la sesión, no lo de la última lectura.
 * Así, si se pierde una lectura —el broker estuvo caído un momento—, la siguiente trae el total
 * correcto igual y nadie cobra de menos. {@code powerKw} es la potencia del último intervalo.
 *
 * <p>{@code eventId} es único por lectura, para que un suscriptor que la reciba dos veces la cuente
 * una sola, como hace Notificaciones con los avisos. {@code endReason} va solo en la última.
 */
public record TelemetryEvent(
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
        /** El conector está cargando. La primera sale al activar, con 0 kWh. */
        READING,
        /** La sesión terminó normalmente: la desactivaron o la batería se llenó. */
        FINISHED,
        /** La sesión terminó porque el cargador falló. */
        FAULT
    }

    public enum EndReason {
        /** Ecopedia la desactivó: llegó al porcentaje objetivo, venció la ventana o el conductor la cortó. */
        STOPPED,
        BATTERY_FULL,
        FAULT
    }
}
