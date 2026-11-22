package com.ecopedia.charging.session.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Una lectura del cargador, tal como llega por el tópico {@code telemetry.charging} (RF16).
 *
 * <p>Es la copia de este artefacto del mensaje que publica el cargador: lo único compartido es el
 * formato JSON, no la clase. Por eso ignora los campos que no conoce: el cargador puede sumar datos
 * sin romper a quien lo escucha.
 *
 * <p>{@code energyKwh} es <b>acumulado</b> desde que arrancó la sesión, no lo del último intervalo:
 * si una lectura se pierde, la siguiente trae el total correcto igual.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryReading(
        String eventId,
        Type type,
        String sessionId,
        Long connectorId,
        Instant timestamp,
        BigDecimal powerKw,
        BigDecimal energyKwh,
        BigDecimal batteryPercent,
        ChargerEndReason endReason) {

    public enum Type {
        /** El conector está cargando. */
        READING,
        /** El cargador dio por terminada la sesión: la apagaron o la batería se llenó. */
        FINISHED,
        /** El cargador cortó la sesión porque falló. */
        FAULT
    }

    /** {@code true} si es la última lectura de la sesión: el cargador ya no entrega energía. */
    public boolean isFinal() {
        return type == Type.FINISHED || type == Type.FAULT;
    }
}
