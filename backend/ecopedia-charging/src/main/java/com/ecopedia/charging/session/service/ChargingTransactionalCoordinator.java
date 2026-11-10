package com.ecopedia.charging.session.service;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingNotFoundException;
import com.ecopedia.charging.booking.domain.BookingRepository;
import com.ecopedia.charging.booking.domain.InvalidBookingRequestException;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.session.domain.ChargeSettlement;
import com.ecopedia.charging.session.domain.ChargerReading;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionNotFoundException;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.ConnectorState;
import com.ecopedia.charging.session.domain.ConnectorStateRepository;
import com.ecopedia.charging.session.domain.SessionEndReason;
import com.ecopedia.charging.session.domain.SessionPaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinador transaccional del flujo de habilitación (RNF06, ECO-60).
 *
 * <p>Ejecuta bajo una <b>misma transacción declarativa</b> ({@code @Transactional}) las escrituras
 * propias del sistema:
 * <ul>
 *   <li>Estado de la reserva: pasa a {@code IN_PROGRESS}.</li>
 *   <li>Estado del conector: pasa a {@code OCCUPIED} con bloqueo optimista.</li>
 *   <li>Estado de la sesión: se persiste la nueva {@link ChargingSession} en estado {@code ACTIVE}.</li>
 * </ul>
 *
 * <p>Ante cualquier falla en la persistencia local o regla de concurrencia, el gestor transaccional
 * realiza un rollback atómico completo de los tres estados, garantizando que el sistema no quede en
 * un estado intermedio ni dos conductores obtengan el mismo slot físico.
 */
@Service
public class ChargingTransactionalCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ChargingTransactionalCoordinator.class);

    private final BookingRepository bookingRepository;
    private final ConnectorStateRepository connectorStateRepository;
    private final ChargingSessionRepository chargingSessionRepository;

    public ChargingTransactionalCoordinator(
            BookingRepository bookingRepository,
            ConnectorStateRepository connectorStateRepository,
            ChargingSessionRepository chargingSessionRepository) {
        this.bookingRepository = bookingRepository;
        this.connectorStateRepository = connectorStateRepository;
        this.chargingSessionRepository = chargingSessionRepository;
    }

    /**
     * Persiste atómicamente la habilitación local de la carga.
     *
     * @return la sesión de carga creada en estado {@code ACTIVE}
     */
    @Transactional(rollbackFor = Exception.class)
    public ChargingSession persistLocalActivation(
            String sessionId,
            Long bookingId,
            Long connectorId,
            Long driverId,
            int targetBatteryPercent,
            BigDecimal powerLimitKw,
            Instant now) {

        Objects.requireNonNull(sessionId, "sessionId no puede ser null");
        Objects.requireNonNull(connectorId, "connectorId no puede ser null");
        Objects.requireNonNull(driverId, "driverId no puede ser null");
        Objects.requireNonNull(powerLimitKw, "powerLimitKw no puede ser null");
        Objects.requireNonNull(now, "now no puede ser null");

        // 1. Estado del conector: validación de ocupación y transición a OCCUPIED
        ConnectorState connectorState = connectorStateRepository
                .findById(connectorId)
                .orElseGet(() -> ConnectorState.available(connectorId, now));

        if (connectorState.isOccupied()) {
            log.warn(
                    "Conflicto de concurrencia: el conector {} ya está ocupado por la sesión {}",
                    connectorId,
                    connectorState.getCurrentSessionId());
            throw new SlotUnavailableException(connectorId);
        }
        if (connectorState.isOutOfService()) {
            throw new InvalidBookingRequestException("El conector " + connectorId + " está fuera de servicio");
        }

        connectorState.occupy(sessionId, now);
        connectorStateRepository.save(connectorState);

        // 2. Estado de la reserva: validación y transición a IN_PROGRESS
        if (bookingId != null) {
            Booking booking =
                    bookingRepository.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));

            if (!booking.belongsTo(driverId)) {
                log.warn(
                        "Acceso denegado en transacción local: la reserva {} no pertenece al conductor {}",
                        bookingId,
                        driverId);
                throw new BookingNotFoundException(bookingId);
            }
            if (!booking.isConfirmed()) {
                throw new InvalidBookingRequestException("La reserva " + bookingId + " no está confirmada");
            }
            booking.startCharging(now);
            bookingRepository.save(booking);
        }

        // 3. Estado de la sesión: creación y persistencia en estado ACTIVE
        ChargingSession session = ChargingSession.start(
                sessionId, bookingId, connectorId, driverId, targetBatteryPercent, powerLimitKw, now);

        ChargingSession savedSession = chargingSessionRepository.save(session);

        log.info(
                "Transacción local de habilitación confirmada: reserva={}, conector={} (OCCUPIED), sesión={} (ACTIVE, {} kW)",
                bookingId,
                connectorId,
                sessionId,
                powerLimitKw);

        return savedSession;
    }

    /**
     * Compensación local si falla el encendido del cargador físico (RNF06, RF13, ECO-60).
     *
     * <p>Marca la sesión como {@code FAULTED} y libera el conector local a {@code AVAILABLE}.
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensateLocalFailure(Long connectorId, String sessionId, Instant now) {
        connectorStateRepository.findById(connectorId).ifPresent(state -> {
            if (sessionId.equals(state.getCurrentSessionId())) {
                state.release(now);
                connectorStateRepository.save(state);
            }
        });

        chargingSessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.markFaulted(now, BigDecimal.ZERO);
            chargingSessionRepository.save(session);
        });

        log.info("Compensación local ejecutada para conector {} y sesión {}", connectorId, sessionId);
    }

    /**
     * Cierra la sesión en la transacción local (RF11, RNF06, ECO-44): la sesión pasa a
     * {@code COMPLETED} con lo que entregó el cargador, el conector vuelve a {@code AVAILABLE} y la
     * reserva a {@code COMPLETED}, con la ventana recortada al instante del cierre.
     *
     * <p>Las tres escrituras juntas o ninguna, igual que al habilitar. Lo externo —apagar el cargador,
     * calcular el importe, cobrarlo— queda afuera: el cargador se apaga antes y el cobro va después,
     * para que la liberación del conector nunca dependa de un tercero.
     *
     * <p>El bloqueo optimista de la sesión es el que decide si dos cierres llegan a la vez —el del
     * conductor y el corte automático de ECO-45—: uno confirma y el otro falla con
     * {@code OptimisticLockingFailureException}, sin liquidar dos veces.
     *
     * @return la sesión cerrada por este pedido, o vacío si ya estaba cerrada: quien llama no tiene
     *     que volver a liquidarla
     */
    @Transactional(rollbackFor = Exception.class)
    public Optional<ChargingSession> persistLocalCompletion(
            String sessionId, ChargerReading reading, SessionEndReason reason, Instant now) {
        ChargingSession session = chargingSessionRepository
                .findBySessionId(sessionId)
                .orElseThrow(() -> new ChargingSessionNotFoundException(sessionId));
        if (!session.isActive()) {
            return Optional.empty();
        }

        session.complete(now, reading, reason);
        ChargingSession saved = chargingSessionRepository.save(session);

        connectorStateRepository.findById(session.getConnectorId()).ifPresent(state -> {
            if (sessionId.equals(state.getCurrentSessionId())) {
                state.release(now);
                connectorStateRepository.save(state);
            }
        });

        if (session.getBookingId() != null) {
            bookingRepository.findById(session.getBookingId()).ifPresent(booking -> {
                if (booking.isInProgress()) {
                    booking.completeCharging(now);
                    bookingRepository.save(booking);
                }
            });
        }

        log.info(
                "Transacción local de cierre confirmada: sesión={} ({}), conector={} (AVAILABLE), reserva={}, {} kWh",
                sessionId,
                reason,
                session.getConnectorId(),
                session.getBookingId(),
                saved.getKwhDelivered());
        return Optional.of(saved);
    }

    /** Anota la liquidación y el resultado del cobro de una sesión ya cerrada (RF14). */
    @Transactional(rollbackFor = Exception.class)
    public ChargingSession recordSettlement(
            String sessionId, ChargeSettlement settlement, SessionPaymentStatus paymentStatus) {
        ChargingSession session = chargingSessionRepository
                .findBySessionId(sessionId)
                .orElseThrow(() -> new ChargingSessionNotFoundException(sessionId));
        if (settlement != null) {
            session.recordSettlement(settlement);
        }
        if (paymentStatus != null) {
            session.recordPayment(paymentStatus);
        }
        return chargingSessionRepository.save(session);
    }
}
