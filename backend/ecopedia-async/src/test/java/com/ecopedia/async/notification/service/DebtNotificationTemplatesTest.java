package com.ecopedia.async.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.async.notification.domain.DebtFacts;
import com.ecopedia.async.notification.domain.NotificationToDispatch;
import com.ecopedia.async.notification.domain.NotificationType;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los textos de los avisos de una deuda (ECO-62, Manuel, 22/11). Enteros a propósito, como los de la
 * seña: es lo que lee el conductor.
 */
class DebtNotificationTemplatesTest {

    private static final NotificationTemplates TEMPLATES =
            new NotificationTemplates(ZoneId.of("America/Argentina/Buenos_Aires"));

    // La carga se cerró el 22/11 a las 23:30 en Buenos Aires: en UTC ya es el 23/11.
    private static final Instant CLOSED = Instant.parse("2026-11-23T02:30:00Z");
    private static final Instant TWENTY_LATER = CLOSED.plusSeconds(20 * 60);

    private static NotificationToDispatch debt(
            NotificationType type, Integer attempt, Integer max, Instant occurred, Instant next) {
        return NotificationToDispatch.ofDebt(
                "debt-1-" + type, type, 5L, new DebtFacts(CLOSED, attempt, max, occurred, next));
    }

    @Test
    @DisplayName("Nace la deuda: de qué carga, cuándo se reintenta y que no puede reservar ni cargar")
    void recorded() {
        NotificationToDispatch recorded = debt(NotificationType.DEBT_RECORDED, null, null, CLOSED, TWENTY_LATER);

        assertThat(TEMPLATES.title(recorded)).isEqualTo("No pudimos cobrar tu carga");
        assertThat(TEMPLATES.body(recorded, null))
                .isEqualTo("No pudimos cobrar la carga del 22/11. Lo vamos a reintentar en 20 minutos. "
                        + "Hasta que se pague, no podés reservar ni cargar.");
    }

    @Test
    @DisplayName("Reintento fallido: el número en el título, el próximo intento y que puede pagarla ya")
    void retryFailed() {
        Instant at = TWENTY_LATER.plusSeconds(20 * 60);
        NotificationToDispatch failed = debt(NotificationType.DEBT_RETRY_FAILED, 2, 3, at, at.plusSeconds(20 * 60));

        assertThat(TEMPLATES.title(failed)).isEqualTo("No pudimos cobrar tu carga (reintento 2 de 3)");
        assertThat(TEMPLATES.body(failed, null))
                .isEqualTo("Volvimos a intentar cobrar la carga del 22/11 y no se pudo. El próximo intento es en 20 "
                        + "minutos. Podés pagarla ahora desde Medios de pago.");
    }

    @Test
    @DisplayName("El último reintento y la marca de la cuenta van en un solo aviso")
    void defaulted() {
        NotificationToDispatch defaulted = debt(NotificationType.DEBT_DEFAULTED, 3, 3, TWENTY_LATER, null);

        assertThat(TEMPLATES.title(defaulted)).isEqualTo("Tu cuenta tiene un pago vencido");
        assertThat(TEMPLATES.body(defaulted, null))
                .isEqualTo("Hicimos el último intento (3 de 3) y no pudimos cobrar la carga del 22/11. Si tenías "
                        + "reservas, las anulamos y te devolvimos sus señas. Para volver a reservar, pagala desde "
                        + "Medios de pago.");
    }

    @Test
    @DisplayName("Pagada: que se cobró y que ya puede reservar")
    void settled() {
        NotificationToDispatch settled = debt(NotificationType.DEBT_SETTLED, null, null, TWENTY_LATER, null);

        assertThat(TEMPLATES.title(settled)).isEqualTo("Pago recibido");
        assertThat(TEMPLATES.body(settled, null)).isEqualTo("Cobramos la carga del 22/11. Ya podés volver a reservar.");
    }

    @Test
    @DisplayName("El plazo sale del evento: con intervalos cortos o sin próximo intento, no inventa los 20 minutos")
    void untilNextAttemptComesFromTheEvent() {
        assertThat(TEMPLATES.body(
                        debt(NotificationType.DEBT_RECORDED, null, null, CLOSED, CLOSED.plusSeconds(20)), null))
                .contains("reintentar en menos de un minuto");
        assertThat(TEMPLATES.body(
                        debt(NotificationType.DEBT_RECORDED, null, null, CLOSED, CLOSED.plusSeconds(60)), null))
                .contains("reintentar en 1 minuto");
        assertThat(TEMPLATES.body(debt(NotificationType.DEBT_RECORDED, null, null, CLOSED, null), null))
                .contains("reintentar más tarde");
        assertThat(TEMPLATES.title(debt(NotificationType.DEBT_RETRY_FAILED, null, 3, CLOSED, null)))
                .isEqualTo("No pudimos cobrar tu carga");
    }
}
