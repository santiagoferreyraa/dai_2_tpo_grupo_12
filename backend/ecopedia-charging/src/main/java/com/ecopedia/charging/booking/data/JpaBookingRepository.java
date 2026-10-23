package com.ecopedia.charging.booking.data;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingRepository;
import com.ecopedia.charging.booking.domain.BookingStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaBookingRepository extends JpaRepository<Booking, Long>, BookingRepository {

    @Override
    List<Booking> findByConnectorIdAndStatus(Long connectorId, BookingStatus status);

    @Override
    List<Booking> findByDriverIdOrderByWindowStartAsc(Long driverId);

    /**
     * El cruce de ventanas, resuelto en la base.
     *
     * <p>Es la misma regla que {@code TimeWindow.overlaps} y está escrita igual —{@code inicio
     * guardado < fin pedido} y {@code fin guardado > inicio pedido}—, así que dos turnos pegados
     * no se pisan. Si alguna vez cambia el criterio, tienen que cambiar los dos: por eso la
     * consulta está acá y no dispersa en el servicio.
     *
     * <p>Se resuelve en la base y no trayendo las reservas del conector a memoria porque el
     * índice {@code idx_bookings_connector_window} está hecho para esto; filtrar en Java
     * obligaría a leer todas las reservas históricas del conector para responder que sí o que no.
     */
    @Override
    @Query(
            """
            select count(b) > 0 from Booking b
            where b.connectorId = :connectorId
              and b.status = :status
              and b.windowStart < :end
              and b.windowEnd > :start
            """)
    boolean existsOverlapping(
            @Param("connectorId") Long connectorId,
            @Param("status") BookingStatus status,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * Las reservas que se cruzan con la ventana, para calcular la disponibilidad. El {@code where}
     * es el de {@link #existsOverlapping} copiado tal cual: si uno cambia, cambia el otro.
     */
    @Override
    @Query(
            """
            select b from Booking b
            where b.connectorId = :connectorId
              and b.status = :status
              and b.windowStart < :end
              and b.windowEnd > :start
            order by b.windowStart
            """)
    List<Booking> findOverlapping(
            @Param("connectorId") Long connectorId,
            @Param("status") BookingStatus status,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * El cruce, pero por conductor y a través de todos los conectores: lo que bloquea al conductor
     * y no al conector.
     *
     * <p>El {@code where} es el mismo de {@link #existsOverlapping} con {@code driverId} en lugar
     * de {@code connectorId} —si cambia el criterio de cruce, cambian los tres—. Lo resuelve el
     * índice {@code idx_bookings_driver}.
     */
    @Override
    @Query(
            """
            select b from Booking b
            where b.driverId = :driverId
              and b.status = :status
              and b.windowStart < :end
              and b.windowEnd > :start
            order by b.windowStart
            """)
    List<Booking> findOverlappingForDriver(
            @Param("driverId") Long driverId,
            @Param("status") BookingStatus status,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * Las reservas del conductor que están corriendo en ese instante.
     *
     * <p>Mismo criterio semiabierto que el cruce, con un instante en vez de una ventana: en el
     * instante exacto del fin la reserva ya terminó, así que ya no bloquea.
     */
    @Override
    @Query(
            """
            select b from Booking b
            where b.driverId = :driverId
              and b.status = :status
              and b.windowStart <= :at
              and b.windowEnd > :at
            order by b.windowStart
            """)
    List<Booking> findInProgressForDriver(
            @Param("driverId") Long driverId, @Param("status") BookingStatus status, @Param("at") Instant at);
}
