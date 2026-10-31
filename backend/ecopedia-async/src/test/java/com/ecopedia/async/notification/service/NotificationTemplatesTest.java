package com.ecopedia.async.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.async.notification.domain.NotificationToDispatch;
import com.ecopedia.async.notification.domain.NotificationType;
import com.ecopedia.async.notification.domain.StationInfo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los textos de los avisos con seña (ECO-53) y de la incomparecencia (ECO-57), que son los que
 * aprobó Manuel el 31/10. Se comparan enteros a propósito: el aviso es lo que lee el conductor, y
 * una palabra de más o de menos es un cambio que alguien tiene que ver.
 */
class NotificationTemplatesTest {

    private static final NotificationTemplates TEMPLATES =
            new NotificationTemplates(ZoneId.of("America/Argentina/Buenos_Aires"));

    // Sábado 1/11 de 18:00 a 19:00 en Buenos Aires.
    private static final Instant START = Instant.parse("2026-11-01T21:00:00Z");
    private static final Instant END = Instant.parse("2026-11-01T22:00:00Z");
    private static final Instant REFUNDABLE_UNTIL = START.minusSeconds(5 * 3600);
    private static final StationInfo PALERMO = new StationInfo(3L, "Estación Palermo", null, null);

    private static NotificationToDispatch event(
            NotificationType type, String amount, Boolean refunded, String lastFour) {
        return new NotificationToDispatch(
                "booking-1-" + type,
                type,
                5L,
                1L,
                12L,
                START,
                END,
                amount == null ? null : new BigDecimal(amount),
                refunded,
                lastFour,
                amount == null ? null : REFUNDABLE_UNTIL);
    }

    @Test
    @DisplayName(
            "No-show con seña: dice que pasó la tolerancia, que la seña no se devuelve y que puede volver a reservar")
    void noShowWithDeposit() {
        NotificationToDispatch noShow = event(NotificationType.BOOKING_NO_SHOW, "8000.00", null, "4242");

        assertThat(TEMPLATES.title(noShow)).isEqualTo("No te presentaste a tu reserva");
        assertThat(TEMPLATES.body(noShow, PALERMO))
                .isEqualTo("No llegaste a tu reserva en Estación Palermo del domingo 1/11 de 18:00 a 19:00. "
                        + "Pasaron los 15 minutos de tolerancia, así que la cancelamos y la seña no se devuelve. "
                        + "Ya podés hacer otra reserva.");
    }

    @Test
    @DisplayName("No-show sin seña: no habla de una seña que no hubo")
    void noShowWithoutDeposit() {
        NotificationToDispatch noShow = event(NotificationType.BOOKING_NO_SHOW, null, null, null);

        assertThat(TEMPLATES.body(noShow, PALERMO))
                .isEqualTo("No llegaste a tu reserva en Estación Palermo del domingo 1/11 de 18:00 a 19:00. "
                        + "Pasaron los 15 minutos de tolerancia, así que la cancelamos. Ya podés hacer otra reserva.");
    }

    @Test
    @DisplayName("Cancelación a tiempo: le reintegramos la seña, sin el monto")
    void cancelledInTime() {
        NotificationToDispatch cancelled = event(NotificationType.BOOKING_CANCELLED, "8000.00", true, "4242");

        assertThat(TEMPLATES.body(cancelled, PALERMO))
                .isEqualTo("Cancelaste tu reserva en Estación Palermo del domingo 1/11 de 18:00 a 19:00. "
                        + "Te reintegramos la seña.");
    }

    @Test
    @DisplayName("Cancelación tardía: el plazo sale del evento, no está escrito en el texto")
    void cancelledLate() {
        NotificationToDispatch cancelled = event(NotificationType.BOOKING_CANCELLED, "8000.50", false, "4242");

        assertThat(TEMPLATES.body(cancelled, PALERMO))
                .isEqualTo("Cancelaste tu reserva en Estación Palermo del domingo 1/11 de 18:00 a 19:00. "
                        + "Como faltaban menos de 5 horas para el inicio, la seña no se devuelve.");
    }

    @Test
    @DisplayName("Cancelación sin seña, o un mensaje de antes de ECO-53: el texto de siempre")
    void cancelledWithoutDeposit() {
        NotificationToDispatch cancelled = event(NotificationType.BOOKING_CANCELLED, null, null, null);

        assertThat(TEMPLATES.body(cancelled, PALERMO))
                .isEqualTo("Cancelaste tu reserva en Estación Palermo del domingo 1/11 de 18:00 a 19:00. "
                        + "El horario quedó libre.");
    }

    @Test
    @DisplayName("La confirmación no menciona la seña aunque la haya")
    void confirmedDoesNotMentionTheDeposit() {
        NotificationToDispatch confirmed = event(NotificationType.BOOKING_CONFIRMED, "8000.00", null, "4242");

        assertThat(TEMPLATES.body(confirmed, PALERMO))
                .isEqualTo("Tu reserva en Estación Palermo está confirmada para el domingo 1/11 de 18:00 a 19:00.");
    }

    @Test
    @DisplayName("Un aviso con nombre y dirección enormes entra igual en la columna")
    void longBodiesFitTheColumn() {
        StationInfo huge = new StationInfo(3L, "E".repeat(200), "D".repeat(300), "CCS2");
        NotificationToDispatch noShow = event(NotificationType.BOOKING_NO_SHOW, "8000", null, "4242");

        String body = TEMPLATES.body(noShow, huge);

        assertThat(body).hasSizeLessThanOrEqualTo(NotificationTemplates.MAX_BODY);
        assertThat(body).doesNotContain("DDDD");
    }
}
