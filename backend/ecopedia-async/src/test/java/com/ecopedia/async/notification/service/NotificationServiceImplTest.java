package com.ecopedia.async.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.async.notification.domain.EmailSender;
import com.ecopedia.async.notification.domain.Notification;
import com.ecopedia.async.notification.domain.NotificationRepository;
import com.ecopedia.async.notification.domain.NotificationToDispatch;
import com.ecopedia.async.notification.domain.NotificationType;
import com.ecopedia.async.notification.domain.StationDirectory;
import com.ecopedia.async.notification.domain.StationInfo;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las reglas de Notificaciones sin Spring ni broker: qué aviso se arma, que un duplicado no se
 * guarda ni se manda dos veces, y que core caído no deja al conductor sin aviso.
 *
 * <p>Que el aviso llegue de verdad por la cola lo prueba {@code BookingNotificationFlowTest}.
 */
class NotificationServiceImplTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Long DRIVER = 7L;

    /** Miércoles 30/09 de 10:00 a 11:00 en Buenos Aires, que en UTC es de 13:00 a 14:00. */
    private static final Instant START = Instant.parse("2026-09-30T13:00:00Z");

    private static final Instant END = Instant.parse("2026-09-30T14:00:00Z");

    private static final StationInfo PALERMO = new StationInfo(3L, "Palermo Soho", "Honduras 4800", "CCS2");

    private final InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
    private final RecordingEmailSender mail = new RecordingEmailSender();

    private NotificationServiceImpl serviceWith(StationDirectory directory) {
        Clock clock = Clock.fixed(Instant.parse("2026-09-28T15:00:00Z"), ZoneOffset.UTC);
        return new NotificationServiceImpl(repository, directory, mail, clock, BUENOS_AIRES);
    }

    private static NotificationToDispatch request(String eventId, NotificationType type) {
        return new NotificationToDispatch(eventId, type, DRIVER, 40L, 12L, START, END);
    }

    @Test
    @DisplayName("Confirmada la reserva, guarda el aviso con la estación y la hora de Buenos Aires, y manda el mail")
    void confirmedBooking() {
        serviceWith(connector -> Optional.of(PALERMO))
                .dispatch(request("booking-40-BOOKING_CONFIRMED", NotificationType.BOOKING_CONFIRMED));

        assertThat(repository.findByRecipientIdOrderByCreatedAtDesc(DRIVER))
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getTitle()).isEqualTo("Reserva confirmada");
                    assertThat(notification.getBody())
                            .isEqualTo("Tu reserva en Palermo Soho (Honduras 4800), conector CCS2 está confirmada"
                                    + " para el miércoles 30/09 de 10:00 a 11:00.");
                    assertThat(notification.getStationName()).isEqualTo("Palermo Soho");
                    assertThat(notification.getBookingId()).isEqualTo(40L);
                    assertThat(notification.isRead()).isFalse();
                });
        assertThat(mail.sent).containsExactly("7 | Reserva confirmada");
    }

    @Test
    @DisplayName("Cancelada la reserva, el aviso dice que el horario quedó libre")
    void cancelledBooking() {
        serviceWith(connector -> Optional.of(PALERMO))
                .dispatch(request("booking-40-BOOKING_CANCELLED", NotificationType.BOOKING_CANCELLED));

        assertThat(repository.findByRecipientIdOrderByCreatedAtDesc(DRIVER))
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getTitle()).isEqualTo("Reserva cancelada");
                    assertThat(notification.getBody()).endsWith("El horario quedó libre.");
                });
    }

    /* La reentrega del broker, o un doble envío: el conductor no puede recibir dos mails iguales. */
    @Test
    @DisplayName("El mismo hecho recibido dos veces se guarda y se manda una sola vez")
    void duplicateIsDiscarded() {
        NotificationServiceImpl service = serviceWith(connector -> Optional.of(PALERMO));
        NotificationToDispatch confirmed = request("booking-40-BOOKING_CONFIRMED", NotificationType.BOOKING_CONFIRMED);

        service.dispatch(confirmed);
        service.dispatch(confirmed);

        assertThat(repository.findByRecipientIdOrderByCreatedAtDesc(DRIVER)).hasSize(1);
        assertThat(mail.sent).hasSize(1);
    }

    @Test
    @DisplayName("Con core caído, el aviso se guarda y se manda igual, nombrando el conector")
    void coreDownStillNotifies() {
        serviceWith(connector -> Optional.empty())
                .dispatch(request("booking-40-BOOKING_CONFIRMED", NotificationType.BOOKING_CONFIRMED));

        assertThat(repository.findByRecipientIdOrderByCreatedAtDesc(DRIVER))
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getBody()).startsWith("Tu reserva del conector 12 está confirmada");
                    assertThat(notification.getStationName()).isNull();
                });
        assertThat(mail.sent).hasSize(1);
    }

    /** El historial en memoria: lo que se prueba acá son las reglas, no el SQL. */
    static class InMemoryNotificationRepository implements NotificationRepository {

        private final List<Notification> rows = new ArrayList<>();

        @Override
        public Notification save(Notification notification) {
            rows.add(notification);
            return notification;
        }

        @Override
        public boolean existsByEventId(String eventId) {
            return rows.stream().anyMatch(row -> row.getEventId().equals(eventId));
        }

        @Override
        public List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId) {
            return rows.stream()
                    .filter(row -> row.getRecipientId().equals(recipientId))
                    .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                    .toList();
        }

        /*
         * Lo que sigue es la lectura y el marcado de la pantalla. Depende de los ids que asigna la
         * base, que esta versión en memoria no tiene, y lo prueba NotificationApiTest contra H2.
         */

        @Override
        public Optional<Notification> findById(Long id) {
            throw new UnsupportedOperationException("Lo prueba NotificationApiTest");
        }

        @Override
        public List<Notification> findTop50ByRecipientIdOrderByIdDesc(Long recipientId) {
            throw new UnsupportedOperationException("Lo prueba NotificationApiTest");
        }

        @Override
        public List<Notification> findTop50ByRecipientIdAndIdGreaterThanOrderByIdDesc(Long recipientId, Long afterId) {
            throw new UnsupportedOperationException("Lo prueba NotificationApiTest");
        }

        @Override
        public long countByRecipientIdAndReadAtIsNull(Long recipientId) {
            throw new UnsupportedOperationException("Lo prueba NotificationApiTest");
        }

        @Override
        public int markAllAsRead(Long recipientId, Instant when) {
            throw new UnsupportedOperationException("Lo prueba NotificationApiTest");
        }
    }

    static class RecordingEmailSender implements EmailSender {

        final List<String> sent = new ArrayList<>();

        @Override
        public void send(Long recipientId, String subject, String body) {
            sent.add(recipientId + " | " + subject);
        }
    }
}
