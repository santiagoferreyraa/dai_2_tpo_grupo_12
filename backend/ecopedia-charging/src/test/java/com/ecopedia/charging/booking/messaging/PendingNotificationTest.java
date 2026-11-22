package com.ecopedia.charging.booking.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecopedia.charging.booking.domain.BookingEvent;
import com.ecopedia.charging.booking.domain.BookingEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;

/**
 * El publicador y el reenvío de avisos, sin broker ni base (ECO-177, QA-27): el {@code JmsTemplate}
 * y la tabla de pendientes son dobles. La prueba con el contexto entero está en
 * {@code BookingWithoutBrokerTest}.
 */
class PendingNotificationTest {

    private static final Instant NOW = Instant.parse("2026-11-10T15:00:00Z");

    private final ObjectMapper json = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private JmsTemplate jms;
    private PendingNotificationRepository pending;
    private JmsBookingEventPublisher publisher;
    private PendingNotificationRelay relay;

    @BeforeEach
    void setUp() {
        jms = mock(JmsTemplate.class);
        pending = mock(PendingNotificationRepository.class);
        publisher = new JmsBookingEventPublisher(
                jms, "notifications.dispatch", pending, json, Clock.fixed(NOW, ZoneOffset.UTC));
        relay = new PendingNotificationRelay(pending, publisher, json);
    }

    private static BookingEvent event(long bookingId) {
        Instant start = NOW.plusSeconds(3600);
        return new BookingEvent(
                "booking-" + bookingId + "-BOOKING_CONFIRMED",
                BookingEventType.BOOKING_CONFIRMED,
                bookingId,
                7L,
                3L,
                start,
                start.plusSeconds(3600),
                NOW,
                BigDecimal.TEN,
                null,
                "4242",
                start.minusSeconds(5 * 3600));
    }

    private void brokerDown() {
        doThrow(new UncategorizedJmsException("Connection refused: localhost:61616"))
                .when(jms)
                .convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));
    }

    private PendingNotification stored(BookingEvent event) throws Exception {
        return new PendingNotification(
                event.eventId(), event.type().name(), event.bookingId(), json.writeValueAsString(event), NOW);
    }

    @Test
    @DisplayName("Con el broker arriba y nada pendiente, el aviso sale directo y no se guarda")
    void publishesDirectly() {
        publisher.publish(event(1));

        verify(jms).convertAndSend(eq("notifications.dispatch"), eq(event(1)), any(MessagePostProcessor.class));
        verify(pending, never()).save(any());
    }

    @Test
    @DisplayName("Con el broker caído, el aviso queda guardado entero para reenviarlo")
    void keepsTheEventWhenTheBrokerIsDown() throws Exception {
        brokerDown();

        publisher.publish(event(1));

        ArgumentCaptor<PendingNotification> saved = ArgumentCaptor.forClass(PendingNotification.class);
        verify(pending).save(saved.capture());
        assertThat(saved.getValue().getEventId()).isEqualTo("booking-1-BOOKING_CONFIRMED");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(json.readValue(saved.getValue().getPayload(), BookingEvent.class))
                .isEqualTo(event(1));
    }

    @Test
    @DisplayName("Si ya hay avisos esperando, el nuevo va detrás y no se adelanta")
    void waitsBehindOlderEvents() {
        when(pending.count()).thenReturn(1L);

        publisher.publish(event(2));

        verify(jms, never()).convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));
        verify(pending).save(any());
    }

    @Test
    @DisplayName("El mismo hecho no se guarda dos veces")
    void doesNotKeepTheSameEventTwice() {
        brokerDown();
        when(pending.existsByEventId("booking-1-BOOKING_CONFIRMED")).thenReturn(true);

        publisher.publish(event(1));

        verify(pending, never()).save(any());
    }

    @Test
    @DisplayName("Cuando el broker vuelve, el barrido publica en orden y borra lo que salió")
    void relaysInOrderWhenTheBrokerIsBack() throws Exception {
        PendingNotification first = stored(event(1));
        PendingNotification second = stored(event(2));
        when(pending.findTop100ByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(first, second));
        List<Object> published = new ArrayList<>();
        doAnswer(call -> published.add(call.getArgument(1)))
                .when(jms)
                .convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));

        assertThat(relay.relay()).isEqualTo(2);

        assertThat(published).containsExactly(event(1), event(2));
        verify(pending).delete(first);
        verify(pending).delete(second);
    }

    @Test
    @DisplayName("Con el broker todavía caído, el barrido corta en el primero y anota el intento")
    void stopsAtTheFirstFailure() throws Exception {
        PendingNotification first = stored(event(1));
        PendingNotification second = stored(event(2));
        when(pending.findTop100ByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(first, second));
        brokerDown();

        assertThat(relay.relay()).isZero();

        verify(jms, times(1)).convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class));
        verify(pending, never()).delete(any());
        verify(pending).save(first);
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).contains("Connection refused");
    }
}
