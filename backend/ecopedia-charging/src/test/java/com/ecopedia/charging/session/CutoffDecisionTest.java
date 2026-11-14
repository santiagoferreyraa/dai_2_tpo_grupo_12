package com.ecopedia.charging.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.charging.session.domain.CutoffDecision;
import com.ecopedia.charging.session.domain.SessionStopReason;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** La regla del corte automático (RF12, ECO-45): corta con la primera de las dos condiciones. */
class CutoffDecisionTest {

    private static final Instant END = Instant.parse("2026-11-13T16:00:00Z");

    @Test
    @DisplayName("Debajo del objetivo y antes del fin de la ventana: sigue cargando")
    void keepsChargingWhileNeitherConditionHolds() {
        assertThat(CutoffDecision.evaluate(new BigDecimal("79.9"), 80, END.minusSeconds(1), END))
                .isEmpty();
    }

    @Test
    @DisplayName("Llega justo al objetivo: corta por objetivo")
    void cutsAtTheTarget() {
        assertThat(CutoffDecision.evaluate(new BigDecimal("80.0"), 80, END.minusSeconds(600), END))
                .contains(SessionStopReason.TARGET_REACHED);
    }

    @Test
    @DisplayName("Lectura del fin de la ventana o después: corta por ventana")
    void cutsAtTheEndOfTheWindow() {
        assertThat(CutoffDecision.evaluate(new BigDecimal("50"), 80, END, END))
                .contains(SessionStopReason.WINDOW_ENDED);
        assertThat(CutoffDecision.evaluate(new BigDecimal("50"), 80, END.plusSeconds(30), END))
                .contains(SessionStopReason.WINDOW_ENDED);
    }

    @Test
    @DisplayName("Las dos a la vez: gana el objetivo, que es lo que el conductor pidió")
    void targetWinsWhenBothHold() {
        assertThat(CutoffDecision.evaluate(new BigDecimal("85"), 80, END.plusSeconds(5), END))
                .contains(SessionStopReason.TARGET_REACHED);
    }

    @Test
    @DisplayName("Sin porcentaje solo puede cortar la ventana; sin ventana, solo el objetivo")
    void missingDataOnlyDisablesItsOwnCondition() {
        assertThat(CutoffDecision.evaluate(null, 80, END.minusSeconds(1), END)).isEmpty();
        assertThat(CutoffDecision.evaluate(null, 80, END, END)).contains(SessionStopReason.WINDOW_ENDED);
        assertThat(CutoffDecision.evaluate(new BigDecimal("50"), 80, END.plusSeconds(3600), null))
                .isEmpty();
        assertThat(CutoffDecision.evaluate(new BigDecimal("100"), 80, END, null))
                .contains(SessionStopReason.TARGET_REACHED);
    }
}
