package com.ecopedia.charging.booking.service;

import com.ecopedia.charging.booking.domain.BookingEvent;
import com.ecopedia.charging.booking.domain.BookingEventPublisher;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Un {@link BookingEventPublisher} que en vez de publicar anota lo que le pidieron.
 *
 * <p>Deja probar QUÉ avisa Reservas y cuándo, sin broker: que el aviso llegue de verdad a la cola
 * lo prueba {@code BookingEventMessagingTest}, con Spring y un Artemis embebido.
 */
class RecordingBookingEventPublisher implements BookingEventPublisher {

    private final List<BookingEvent> published = new CopyOnWriteArrayList<>();

    @Override
    public void publish(BookingEvent event) {
        published.add(event);
    }

    List<BookingEvent> published() {
        return List.copyOf(published);
    }
}
