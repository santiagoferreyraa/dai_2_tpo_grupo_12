package com.ecopedia.charging.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.charging.booking.domain.ConnectorCatalog;
import com.ecopedia.charging.booking.domain.ConnectorSnapshot;
import com.ecopedia.charging.booking.domain.Hold;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.booking.domain.TimeWindow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La garantía que sostiene todo el componente: dos conductores no se llevan el mismo slot.
 *
 * <p><b>Por qué hace falta una prueba con hilos de verdad.</b> El resto de las pruebas llama al
 * servicio de a una operación por vez, y así el candado de {@code BookingServiceImpl} no se
 * ejerce nunca: la misma prueba pasaría con el candado borrado. Lo que puede fallar acá es una
 * carrera de dos pasos —dos conductores revisan a la vez, los dos ven el slot libre, los dos lo
 * toman— y esa solo aparece cuando hay concurrencia real.
 *
 * <p>Todos los hilos esperan en la misma barrera y arrancan juntos, para que la ventana en que
 * pueden pisarse sea lo más grande posible en vez de depender de que el planificador los cruce
 * por casualidad.
 */
class BookingConcurrencyTest {

    private static final int DRIVERS = 32;

    @Test
    @DisplayName("Con 32 conductores compitiendo por el mismo slot, gana exactamente uno")
    void onlyOneDriverGetsTheSlot() throws InterruptedException {
        ConnectorCatalog available = id -> Optional.of(new ConnectorSnapshot(id, 1L, "AVAILABLE"));
        BookingServiceImpl service = new BookingServiceImpl(
                available,
                new InMemoryBookingRepository(),
                new RecordingBookingEventPublisher(),
                Clock.systemUTC(),
                Duration.ofMinutes(10),
                Duration.ofHours(4),
                Duration.ofDays(30));
        service.start();

        Instant start = Instant.now().plus(Duration.ofDays(1));
        TimeWindow window = new TimeWindow(start, start.plus(Duration.ofHours(1)));

        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(DRIVERS);
        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        for (int i = 0; i < DRIVERS; i++) {
            long driverId = 100L + i;
            Thread.ofPlatform().start(() -> {
                try {
                    startLine.await();
                    Hold hold = service.startHold(7L, window, driverId);
                    service.confirmBooking(hold.id(), driverId);
                    confirmed.incrementAndGet();
                } catch (SlotUnavailableException expected) {
                    // El resultado correcto para todos menos uno.
                    rejected.incrementAndGet();
                } catch (Exception other) {
                    unexpected.incrementAndGet();
                } finally {
                    finished.countDown();
                }
            });
        }

        startLine.countDown();
        finished.await();

        assertThat(confirmed).hasValue(1);
        assertThat(rejected).hasValue(DRIVERS - 1);
        // Nada de fallas raras: los perdedores pierden por la regla, no por una excepción suelta.
        assertThat(unexpected).hasValue(0);

        service.stop();
    }
}
