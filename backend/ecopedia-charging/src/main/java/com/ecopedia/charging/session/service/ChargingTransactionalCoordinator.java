package com.ecopedia.charging.session.service;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingNotFoundException;
import com.ecopedia.charging.booking.domain.BookingRepository;
import com.ecopedia.charging.booking.domain.InvalidBookingRequestException;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.checkout.domain.ConsumptionPaymentStatus;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.ConnectorState;
import com.ecopedia.charging.session.domain.ConnectorStateRepository;
import com.ecopedia.charging.session.domain.SessionNotFoundException;
import com.ecopedia.charging.session.domain.SessionStopReason;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final Duration earlyActivation;

    public ChargingTransactionalCoordinator(
            BookingRepository bookingRepository,
            ConnectorStateRepository connectorStateRepository,
            ChargingSessionRepository chargingSessionRepository,
            @Value("${ecopedia.booking.early-activation:PT15M}") Duration earlyActivation) {
        this.bookingRepository = bookingRepository;
        this.connectorStateRepository = connectorStateRepository;
        this.chargingSessionRepository = chargingSessionRepository;
        this.earlyActivation = earlyActivation;
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
            // Si la reserva dejó de estar confirmada, o todavía no es su rango de habilitación, la
            // propia reserva lo rechaza con 409 (patrón State).
            booking.startCharging(now, earlyActivation);
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
     * Cierra atómicamente la sesión local, libera el conector y finaliza la reserva recortando la
     * ventana remanente (Flujo C, RF12, RNF06, ECO-55).
     */
    @Transactional(rollbackFor = Exception.class)
    public CompletedSessionData completeLocalSession(String sessionId, BigDecimal kwhDelivered, Instant stoppedAt) {
        Objects.requireNonNull(sessionId, "sessionId no puede ser null");
        Objects.requireNonNull(stoppedAt, "stoppedAt no puede ser null");

        ChargingSession session = chargingSessionRepository
                .findBySessionId(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (!session.isActive()) {
            throw new SessionAlreadyClosedException(sessionId);
        }

        session.stop(stoppedAt, kwhDelivered);
        ChargingSession savedSession = chargingSessionRepository.save(session);

        connectorStateRepository.findById(savedSession.getConnectorId()).ifPresent(state -> {
            if (sessionId.equals(state.getCurrentSessionId())) {
                state.release(stoppedAt);
                connectorStateRepository.save(state);
            }
        });

        BigDecimal depositAmount = BigDecimal.ZERO;
        Instant originalWindowEnd = null;
        if (savedSession.getBookingId() != null) {
            Booking booking = bookingRepository
                    .findById(savedSession.getBookingId())
                    .orElseThrow(() -> new BookingNotFoundException(savedSession.getBookingId()));
            originalWindowEnd = booking.getWindow().end();
            depositAmount = booking.getDepositAmount() != null ? booking.getDepositAmount() : BigDecimal.ZERO;
            booking.completeCharging(stoppedAt);
            bookingRepository.save(booking);
        }

        log.info(
                "Sesión {} cerrada localmente: conector={} liberado, kWh={}",
                sessionId,
                savedSession.getConnectorId(),
                savedSession.getKwhDelivered());

        return new CompletedSessionData(savedSession, depositAmount, originalWindowEnd);
    }

    public record CompletedSessionData(ChargingSession session, BigDecimal depositAmount, Instant originalWindowEnd) {}

    /**
     * Anota en la sesión ya cerrada cómo se liquidó (RF14, ECO-44). Va en su propia transacción,
     * después del cobro, que es externo: si esto fallara, la sesión ya está cerrada y el conector
     * libre, que es lo que no puede esperar.
     */
    @Transactional(rollbackFor = Exception.class)
    public ChargingSession recordSettlement(
            String sessionId,
            SessionStopReason stopReason,
            BigDecimal depositApplied,
            BigDecimal realCost,
            BigDecimal finalBilled,
            BigDecimal additionalCharge,
            ConsumptionPaymentStatus paymentStatus) {
        ChargingSession session = chargingSessionRepository
                .findBySessionId(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        session.recordSettlement(stopReason, depositApplied, realCost, finalBilled, additionalCharge, paymentStatus);
        return chargingSessionRepository.save(session);
    }

    /**
     * La sesión ya estaba cerrada cuando llegó este cierre: la cerró otro camino (el corte automático,
     * un reintento). Sigue siendo un {@link InvalidBookingRequestException}, como antes de ECO-44,
     * pero con nombre propio para que quien cierra la distinga y no liquide dos veces.
     */
    public static class SessionAlreadyClosedException extends InvalidBookingRequestException {

        public SessionAlreadyClosedException(String sessionId) {
            super("La sesión " + sessionId + " ya fue finalizada");
        }
    }
}
