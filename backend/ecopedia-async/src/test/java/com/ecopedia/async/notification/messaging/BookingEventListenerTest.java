package com.ecopedia.async.notification.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ecopedia.async.notification.domain.NotificationService;
import com.ecopedia.async.notification.domain.NotificationType;
import jakarta.jms.Message;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.jms.support.converter.MessageConverter;

/**
 * Pruebas unitarias de {@link BookingEventListener} cubriendo payloads inválidos y reintentos hacia DLQ.
 */
class BookingEventListenerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final MessageConverter converter = mock(MessageConverter.class);

    private BookingEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new BookingEventListener(notificationService, converter, "notifications.dispatch", 3);
    }

    @Test
    @DisplayName(
            "Mensaje con payload que no es BookingEventMessage y sin JMSMessageID falla con MessageConversionException")
    void unreadablePayloadWithoutJmsIdThrowsConversionException() throws Exception {
        Message message = mock(Message.class);
        when(message.propertyExists("JMSXDeliveryCount")).thenReturn(false);
        when(message.getJMSMessageID()).thenReturn(null);
        when(converter.fromMessage(message)).thenReturn("texto-plano");

        assertThatThrownBy(() -> listener.onBookingEvent(message)).isInstanceOf(MessageConversionException.class);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Falla al despachar: propaga la excepción tanto antes del último intento como en el intento final")
    void dispatchFailurePropagatesBeforeAndAtMaxAttempts() throws Exception {
        BookingEventMessage event = new BookingEventMessage(
                "evt-1",
                NotificationType.BOOKING_CONFIRMED,
                10L,
                5L,
                2L,
                Instant.parse("2026-11-10T15:00:00Z"),
                Instant.parse("2026-11-10T16:00:00Z"),
                Instant.parse("2026-11-10T14:00:00Z"),
                BigDecimal.ZERO,
                null,
                null,
                null);

        Message retryMessage = mock(Message.class);
        when(retryMessage.propertyExists("JMSXDeliveryCount")).thenReturn(true);
        when(retryMessage.getIntProperty("JMSXDeliveryCount")).thenReturn(1);
        when(converter.fromMessage(retryMessage)).thenReturn(event);
        doThrow(new IllegalStateException("db down")).when(notificationService).dispatch(any());

        assertThatThrownBy(() -> listener.onBookingEvent(retryMessage)).isInstanceOf(IllegalStateException.class);

        Message finalAttemptMessage = mock(Message.class);
        when(finalAttemptMessage.propertyExists("JMSXDeliveryCount")).thenReturn(true);
        when(finalAttemptMessage.getIntProperty("JMSXDeliveryCount")).thenReturn(3);
        when(converter.fromMessage(finalAttemptMessage)).thenReturn(event);

        assertThatThrownBy(() -> listener.onBookingEvent(finalAttemptMessage))
                .isInstanceOf(IllegalStateException.class);

        verify(notificationService, org.mockito.Mockito.times(2)).dispatch(any());
    }
}
