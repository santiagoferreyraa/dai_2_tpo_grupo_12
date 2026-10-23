package com.ecopedia.charging.booking.service;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingRepository;
import com.ecopedia.charging.booking.domain.BookingStatus;
import com.ecopedia.charging.booking.domain.TimeWindow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * El puerto de persistencia, resuelto en memoria para probar el servicio sin base.
 *
 * <p>El cruce de ventanas se evalúa con {@link TimeWindow#overlaps}, que es la misma regla que
 * escribe en JPQL {@code JpaBookingRepository}: si las dos se separaran, este doble diría que sí
 * donde la base dice que no y las pruebas pasarían sobre una mentira. Que la consulta real haga
 * lo mismo lo verifica {@code BookingApiTest}, que sí levanta la base.
 *
 * <p>El id lo asigna acá un contador, porque en producción lo asigna la base y la entidad no
 * expone cómo. Se escribe por reflexión a propósito: agregarle un setter a {@code Booking} solo
 * para los tests dejaría abierta en producción una puerta que nadie debería usar.
 */
class InMemoryBookingRepository implements BookingRepository {

    private final Map<Long, Booking> saved = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public Booking save(Booking booking) {
        if (booking.getId() == null) {
            ReflectionTestUtils.setField(booking, "id", nextId++);
        }
        saved.put(booking.getId(), booking);
        return booking;
    }

    @Override
    public Optional<Booking> findById(Long bookingId) {
        return Optional.ofNullable(saved.get(bookingId));
    }

    @Override
    public List<Booking> findByConnectorIdAndStatus(Long connectorId, BookingStatus status) {
        return saved.values().stream()
                .filter(booking -> booking.getConnectorId().equals(connectorId))
                .filter(booking -> booking.getStatus() == status)
                .toList();
    }

    @Override
    public boolean existsOverlapping(Long connectorId, BookingStatus status, Instant start, Instant end) {
        TimeWindow window = new TimeWindow(start, end);
        return findByConnectorIdAndStatus(connectorId, status).stream()
                .anyMatch(booking -> booking.getWindow().overlaps(window));
    }

    @Override
    public List<Booking> findOverlapping(Long connectorId, BookingStatus status, Instant start, Instant end) {
        TimeWindow window = new TimeWindow(start, end);
        return findByConnectorIdAndStatus(connectorId, status).stream()
                .filter(booking -> booking.getWindow().overlaps(window))
                .sorted(Comparator.comparing(booking -> booking.getWindow().start()))
                .toList();
    }

    @Override
    public List<Booking> findOverlappingForDriver(Long driverId, BookingStatus status, Instant start, Instant end) {
        TimeWindow window = new TimeWindow(start, end);
        return findByDriverIdOrderByWindowStartAsc(driverId).stream()
                .filter(booking -> booking.getStatus() == status)
                .filter(booking -> booking.getWindow().overlaps(window))
                .toList();
    }

    @Override
    public List<Booking> findInProgressForDriver(Long driverId, BookingStatus status, Instant at) {
        return findByDriverIdOrderByWindowStartAsc(driverId).stream()
                .filter(booking -> booking.getStatus() == status)
                .filter(booking -> !booking.getWindow().start().isAfter(at)
                        && booking.getWindow().end().isAfter(at))
                .toList();
    }

    @Override
    public List<Booking> findByDriverIdOrderByWindowStartAsc(Long driverId) {
        List<Booking> ofDriver = new ArrayList<>(saved.values().stream()
                .filter(booking -> booking.getDriverId().equals(driverId))
                .toList());
        ofDriver.sort(Comparator.comparing(booking -> booking.getWindow().start()));
        return ofDriver;
    }
}
