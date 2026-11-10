package com.ecopedia.charging.session.service;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingNotFoundException;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.session.domain.ChargeSettlement;
import com.ecopedia.charging.session.domain.ChargerReading;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionNotFoundException;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.ChargingSessionService;
import com.ecopedia.charging.session.domain.ChargingSessionView;
import com.ecopedia.charging.session.domain.ConsumptionPaymentUnavailableException;
import com.ecopedia.charging.session.domain.ConsumptionPayments;
import com.ecopedia.charging.session.domain.ConsumptionPricing;
import com.ecopedia.charging.session.domain.ConsumptionPricingUnavailableException;
import com.ecopedia.charging.session.domain.PhysicalChargerPort;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import com.ecopedia.charging.session.domain.SessionEndReason;
import com.ecopedia.charging.session.domain.SessionPaymentStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * El resto de la vida de una sesión de carga, después de la habilitación: consultarla y terminarla
 * (RF11, RF14, RNF06, ECO-44).
 *
 * <p>Terminar es una secuencia con un orden que no se negocia:
 * <ol>
 *   <li><b>Apagar el cargador</b> y leer cuánto entregó. Si no contesta, se sigue igual con lo último
 *       que se sabía: la liberación del conector no puede depender de un tercero (RNF06).
 *   <li><b>Cerrar en la transacción local</b> la sesión, el conector y la reserva
 *       ({@link ChargingTransactionalCoordinator#persistLocalCompletion}).
 *   <li><b>Liquidar:</b> el consumo según Tarificación, contra la seña ya cobrada, que es el piso de
 *       una reserva y no existe en una carga sin reserva ({@link ChargingSettlementPolicy}).
 *   <li><b>Cobrar la diferencia</b> en Pagos. Un rechazo de la pasarela no deshace nada: Pagos lo
 *       anota como deuda pendiente. Si Tarificación o Pagos no contestan, la sesión queda cerrada con
 *       el cobro en {@link SessionPaymentStatus#PENDING}.
 * </ol>
 *
 * <p>Este método es el que va a llamar también el corte automático de ECO-45, con otro
 * {@link SessionEndReason}: por eso {@link #finish} es independiente del conductor.
 */
@Service
public class ChargingSessionServiceImpl implements ChargingSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChargingSessionServiceImpl.class);

    private final ChargingSessionRepository sessionRepository;
    private final PhysicalChargerPort physicalChargerPort;
    private final ChargingTransactionalCoordinator transactionalCoordinator;
    private final ChargingSettlementPolicy settlementPolicy;
    private final ConsumptionPricing consumptionPricing;
    private final ConsumptionPayments consumptionPayments;
    private final BookingService bookingService;
    private final Clock clock;

    public ChargingSessionServiceImpl(
            ChargingSessionRepository sessionRepository,
            PhysicalChargerPort physicalChargerPort,
            ChargingTransactionalCoordinator transactionalCoordinator,
            ChargingSettlementPolicy settlementPolicy,
            ConsumptionPricing consumptionPricing,
            ConsumptionPayments consumptionPayments,
            BookingService bookingService,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.physicalChargerPort = physicalChargerPort;
        this.transactionalCoordinator = transactionalCoordinator;
        this.settlementPolicy = settlementPolicy;
        this.consumptionPricing = consumptionPricing;
        this.consumptionPayments = consumptionPayments;
        this.bookingService = bookingService;
        this.clock = clock;
    }

    @Override
    public ChargingSessionView getSession(String sessionId, Long driverId) {
        return ChargingSessionView.of(ownedSession(sessionId, driverId));
    }

    @Override
    public Optional<ChargingSessionView> findActiveSession(Long driverId) {
        return sessionRepository.findActiveByDriverId(driverId).map(ChargingSessionView::of);
    }

    @Override
    public ChargingSessionView stop(String sessionId, Long driverId) {
        ChargingSession session = ownedSession(sessionId, driverId);
        if (!session.isActive()) {
            // Ya terminó —la detuvo antes, o la cortó el sistema—: se devuelve cómo terminó.
            return ChargingSessionView.of(session);
        }
        return ChargingSessionView.of(finish(session, SessionEndReason.DRIVER_STOPPED));
    }

    /**
     * Termina una sesión activa por el motivo que sea, y la liquida. Ver la secuencia en la
     * documentación de la clase.
     *
     * @return la sesión como quedó, con la liquidación y el resultado del cobro
     */
    ChargingSession finish(ChargingSession session, SessionEndReason reason) {
        String sessionId = session.getSessionId();

        // 1. Apagar el cargador (externo, fuera de la transacción)
        ChargerReading reading = null;
        try {
            reading = physicalChargerPort.deactivate(session.getConnectorId()).orElse(null);
        } catch (PhysicalChargerUnavailableException unreachable) {
            log.warn(
                    "El cargador del conector {} no contestó al apagar la sesión {}: se cierra con lo último que se sabía ({} kWh)",
                    session.getConnectorId(),
                    sessionId,
                    session.getKwhDelivered());
        }

        // 2. Cerrar en la transacción local: sesión, conector y reserva
        Optional<ChargingSession> closedNow;
        try {
            closedNow = transactionalCoordinator.persistLocalCompletion(sessionId, reading, reason, clock.instant());
        } catch (OptimisticLockingFailureException closedMeanwhile) {
            // Otro cierre (el corte automático, un doble clic) se cruzó con este y confirmó primero.
            log.info("La sesión {} se cerró por otro camino mientras se la detenía", sessionId);
            closedNow = Optional.empty();
        }
        if (closedNow.isEmpty()) {
            // Ya estaba cerrada: la liquida quien la cerró, no se liquida dos veces.
            return sessionRepository
                    .findBySessionId(sessionId)
                    .orElseThrow(() -> new ChargingSessionNotFoundException(sessionId));
        }
        ChargingSession closed = closedNow.get();

        // 3. Liquidar: consumo real contra la seña
        ChargeSettlement settlement;
        try {
            BigDecimal realCost =
                    consumptionPricing.costOf(closed.getConnectorId(), closed.getKwhDelivered(), closed.getStartedAt());
            settlement = settlementPolicy.settleCompletedSession(depositOf(closed), realCost);
        } catch (ConsumptionPricingUnavailableException noPrice) {
            log.warn(
                    "No se pudo calcular el importe de la sesión {} ({} kWh): queda cerrada con el cobro pendiente",
                    sessionId,
                    closed.getKwhDelivered());
            return transactionalCoordinator.recordSettlement(sessionId, null, SessionPaymentStatus.PENDING);
        }
        ChargingSession settled = transactionalCoordinator.recordSettlement(sessionId, settlement, null);
        if (!settlement.requiresAdditionalCharge()) {
            log.info(
                    "Sesión {} liquidada sin cobro adicional: consumo ${}, seña ${}",
                    sessionId,
                    settlement.realCost(),
                    settlement.depositAmount());
            return settled;
        }

        // 4. Cobrar la diferencia (externo, después del cierre local)
        SessionPaymentStatus paymentStatus;
        try {
            paymentStatus = consumptionPayments.charge(
                    closed.getDriverId(), settlement.amountToCharge(), "session-" + sessionId);
        } catch (ConsumptionPaymentUnavailableException noAnswer) {
            log.warn(
                    "Pagos no contestó al cobrar ${} de la sesión {}: queda pendiente",
                    settlement.amountToCharge(),
                    sessionId);
            paymentStatus = SessionPaymentStatus.PENDING;
        }
        log.info(
                "Sesión {} liquidada: consumo ${}, seña ${}, a cobrar ${} → {}",
                sessionId,
                settlement.realCost(),
                settlement.depositAmount(),
                settlement.amountToCharge(),
                paymentStatus);
        return transactionalCoordinator.recordSettlement(sessionId, null, paymentStatus);
    }

    /**
     * La seña cobrada por la reserva de la sesión: es el piso de facturación (RF14). Una carga sin
     * reserva tiene una reserva instantánea sin seña, así que da 0 y no hay piso.
     */
    private BigDecimal depositOf(ChargingSession session) {
        if (session.getBookingId() == null) {
            return BigDecimal.ZERO;
        }
        try {
            Booking booking = bookingService.getBooking(session.getBookingId());
            BigDecimal deposit = booking.getDepositAmount();
            return deposit == null || deposit.signum() < 0 ? BigDecimal.ZERO : deposit;
        } catch (BookingNotFoundException missing) {
            log.warn(
                    "La sesión {} apunta a la reserva {}, que no existe",
                    session.getSessionId(),
                    session.getBookingId());
            return BigDecimal.ZERO;
        }
    }

    private ChargingSession ownedSession(String sessionId, Long driverId) {
        return sessionRepository
                .findBySessionId(sessionId)
                .filter(session -> session.belongsTo(driverId))
                .orElseThrow(() -> new ChargingSessionNotFoundException(sessionId));
    }
}
