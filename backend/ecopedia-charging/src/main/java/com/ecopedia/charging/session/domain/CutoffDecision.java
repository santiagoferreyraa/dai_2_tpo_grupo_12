package com.ecopedia.charging.session.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * La regla del corte automático (RF12, ECO-45): una sesión se corta al cumplirse <b>la primera</b> de
 * dos condiciones, evaluadas contra la lectura de telemetría que acaba de llegar.
 *
 * <ol>
 *   <li>La batería llegó al porcentaje objetivo.
 *   <li>La lectura es del fin de la ventana o posterior: la reservada, o la calculada para una carga sin
 *       reserva.
 * </ol>
 *
 * <p>Si las dos se cumplen en la misma lectura gana el objetivo: el conductor obtuvo lo que pidió, y
 * es lo que le corresponde leer en la pantalla.
 *
 * <p>Es una función pura —sin base, sin reloj, sin cargador— para que la regla se pruebe sola. La
 * telemetría trae el instante de la lectura, y es contra ese instante que se mide la ventana: si la
 * lectura llegó tarde porque el broker estuvo caído, lo que cuenta es cuándo midió el cargador.
 */
public final class CutoffDecision {

    private CutoffDecision() {}

    /**
     * @param batteryPercent el porcentaje de la lectura; {@code null} si el cargador no lo sabe, y
     *     entonces solo puede cortar la ventana
     * @param windowEnd el fin de la ventana; {@code null} si no se conoce, y entonces solo puede cortar
     *     el objetivo
     * @return por qué cortar, o vacío si la sesión sigue
     */
    public static Optional<SessionStopReason> evaluate(
            BigDecimal batteryPercent, int targetBatteryPercent, Instant readingAt, Instant windowEnd) {
        if (batteryPercent != null && batteryPercent.compareTo(BigDecimal.valueOf(targetBatteryPercent)) >= 0) {
            return Optional.of(SessionStopReason.TARGET_REACHED);
        }
        if (windowEnd != null && readingAt != null && !readingAt.isBefore(windowEnd)) {
            return Optional.of(SessionStopReason.WINDOW_ENDED);
        }
        return Optional.empty();
    }
}
