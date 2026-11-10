package com.ecopedia.charging.session.service;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingNotFoundException;
import com.ecopedia.charging.booking.domain.BookingRepository;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.booking.domain.BookingStateConflictException;
import com.ecopedia.charging.booking.domain.ConnectorNotBookableException;
import com.ecopedia.charging.booking.domain.ConnectorNotFoundException;
import com.ecopedia.charging.booking.domain.InvalidBookingRequestException;
import com.ecopedia.charging.checkout.domain.ConsumptionPaymentStatus;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.PaymentMethodsUnavailableException;
import com.ecopedia.charging.checkout.domain.PricingUnavailableException;
import com.ecopedia.charging.checkout.domain.TariffDirectory;
import com.ecopedia.charging.session.domain.ActivationFailureReason;
import com.ecopedia.charging.session.domain.ActivationRequest;
import com.ecopedia.charging.session.domain.ActivationResult;
import com.ecopedia.charging.session.domain.ChargeSettlement;
import com.ecopedia.charging.session.domain.ChargingPoint;
import com.ecopedia.charging.session.domain.ChargingPointCatalog;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.PhysicalChargerPort;
import com.ecopedia.charging.session.domain.PhysicalChargerRejectedException;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import com.ecopedia.charging.session.domain.PowerDecision;
import com.ecopedia.charging.session.domain.SessionNotFoundException;
import com.ecopedia.charging.session.domain.SessionStatus;
import com.ecopedia.charging.session.domain.SessionStopResult;
import com.ecopedia.charging.session.service.ChargingTransactionalCoordinator.CompletedSessionData;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orquestador del flujo de habilitación de carga (Flujo B, RF11, RNF06, ECO-60).
 *
 * <p>Secuencia de habilitación:
 * <ol>
 *   <li><b>Validaciones y llamadas externas previas:</b> consulta de catálogo de terminales,
 *       factibilidad eléctrica a la distribuidora (SOAP) y verificación de tarjeta cobrable sin deuda
 *       (REST). Ninguna mueve dinero ni participa de la transacción local.
 *   <li><b>Transacción declarativa local:</b> persistencia atómica de la reserva ({@code IN_PROGRESS}),
 *       el conector ({@code OCCUPIED}) y la sesión ({@code ACTIVE}) vía {@link ChargingTransactionalCoordinator}.
 *   <li><b>Activación física:</b> orden de encendido al cargador por {@link PhysicalChargerPort}.
 *       Si el hardware no responde o rechaza la orden, el sistema no hace rollback de operaciones
 *       externas sino una <b>compensación explícita</b>: libera el conector, marca la sesión como
 *       {@code FAULTED} y encola el reintegro del 100% de la seña sin penalización (RF13, ECO-59).
 * </ol>
 */
@Service
public class ChargingActivationService {

    private static final Logger log = LoggerFactory.getLogger(ChargingActivationService.class);

    private final ChargingPointCatalog chargingPointCatalog;
    private final ChargingPowerPolicy chargingPowerPolicy;
    private final PaymentDirectory paymentDirectory;
    private final BookingService bookingService;
    private final ChargingSettlementPolicy settlementPolicy;
    private final ChargingTransactionalCoordinator transactionalCoordinator;
    private final PhysicalChargerPort physicalChargerPort;
    private final ChargingSessionRepository chargingSessionRepository;
    private final BookingRepository bookingRepository;
    private final TariffDirectory tariffDirectory;
    private final Clock clock;

    public ChargingActivationService(
            ChargingPointCatalog chargingPointCatalog,
            ChargingPowerPolicy chargingPowerPolicy,
            PaymentDirectory paymentDirectory,
            BookingService bookingService,
            ChargingSettlementPolicy settlementPolicy,
            ChargingTransactionalCoordinator transactionalCoordinator,
            PhysicalChargerPort physicalChargerPort,
            ChargingSessionRepository chargingSessionRepository,
            BookingRepository bookingRepository,
            TariffDirectory tariffDirectory,
            Clock clock) {
        this.chargingPointCatalog = chargingPointCatalog;
        this.chargingPowerPolicy = chargingPowerPolicy;
        this.paymentDirectory = paymentDirectory;
        this.bookingService = bookingService;
        this.settlementPolicy = settlementPolicy;
        this.transactionalCoordinator = transactionalCoordinator;
        this.physicalChargerPort = physicalChargerPort;
        this.chargingSessionRepository = chargingSessionRepository;
        this.bookingRepository = bookingRepository;
        this.tariffDirectory = tariffDirectory;
        this.clock = clock;
    }

    public ActivationResult activate(ActivationRequest request, String authToken) {
        Objects.requireNonNull(request, "request no puede ser null");
        Instant now = clock.instant();

        // 0. Verificación previa de la reserva (pertenencia, conector y estado) antes de llamadas externas
        if (request.bookingId() != null) {
            Booking booking = bookingService.getBooking(request.bookingId());
            if (!booking.belongsTo(request.driverId())) {
                log.warn(
                        "Acceso denegado: el conductor {} quiso habilitar con la reserva {}, que es de otro",
                        request.driverId(),
                        request.bookingId());
                throw new BookingNotFoundException(request.bookingId());
            }
            if (!booking.getConnectorId().equals(request.connectorId())) {
                throw new InvalidBookingRequestException("La reserva es de otro conector");
            }
            if (!booking.isConfirmed()) {
                // Antes de llamar a nadie, el mismo 409 que daría la propia reserva (patrón State).
                throw new BookingStateConflictException(
                        request.bookingId(), "en " + booking.getStatus(), "la habilitación de la carga");
            }
        }

        // 1. Validaciones y consultas externas previas (FUERA de la transacción declarativa)
        ChargingPoint chargingPoint = chargingPointCatalog
                .find(request.connectorId())
                .orElseThrow(() -> new ConnectorNotFoundException(request.connectorId()));

        PowerDecision powerDecision = chargingPowerPolicy.decide(chargingPoint);
        if (!powerDecision.canStart()) {
            if (request.bookingId() != null) {
                settlementPolicy.failActivation(request.bookingId(), ActivationFailureReason.REJECTED_BY_GRID);
            }
            throw new ConnectorNotBookableException(
                    request.connectorId(), "La distribuidora eléctrica rechazó la habilitación de la demanda");
        }

        // Validación síncrona completa contra la pasarela y deudas (RF11, RF14, ECO-54)
        paymentDirectory.validatePaymentMethodForCharging(request.driverId(), null);

        // Registro de reserva instantánea si es carga sin reserva previa (RF10, ECO-58)
        Long effectiveBookingId = request.bookingId();
        if (effectiveBookingId == null) {
            Booking walkInBooking = bookingService.registerWalkIn(request.connectorId(), request.driverId());
            effectiveBookingId = walkInBooking.getId();
        }

        // 2. Transacción declarativa local (RNF06): reserva + conector + sesión bajo la misma tx
        String sessionId = UUID.randomUUID().toString();
        ChargingSession session;
        try {
            session = transactionalCoordinator.persistLocalActivation(
                    sessionId,
                    effectiveBookingId,
                    request.connectorId(),
                    request.driverId(),
                    request.targetBatteryPercent(),
                    powerDecision.powerKw(),
                    now);
        } catch (RuntimeException persistFailure) {
            if (request.bookingId() == null && effectiveBookingId != null) {
                settlementPolicy.failActivation(effectiveBookingId, ActivationFailureReason.CHARGER_UNAVAILABLE);
            }
            throw persistFailure;
        }

        // 3. Activación física del hardware externo (NO participa del rollback de base de datos)
        try {
            physicalChargerPort.activate(request.connectorId(), sessionId, powerDecision.powerKw());
        } catch (PhysicalChargerRejectedException | PhysicalChargerUnavailableException chargerFailure) {
            log.error(
                    "Falla en el cargador físico al activar el conector {}: aplicando compensación RNF06 / RF13",
                    request.connectorId(),
                    chargerFailure);

            // Compensación local: marcar sesión FAULTED y liberar conector local
            transactionalCoordinator.compensateLocalFailure(request.connectorId(), sessionId, clock.instant());

            // Compensación de seña (RF13, ECO-59) o liberación de reserva walk-in (ECO-58)
            if (effectiveBookingId != null) {
                settlementPolicy.failActivation(effectiveBookingId, ActivationFailureReason.CHARGER_UNAVAILABLE);
            }

            throw chargerFailure;
        }

        return ActivationResult.fromSession(session);
    }

    /**
     * Detiene una sesión de carga iniciada por el conductor (Flujo C, RF12, RF14, ECO-55).
     *
     * <p>Libera primero el cargador físico y la transacción local en base de datos, para garantizar
     * que una caída de Tarificación o Pagos nunca deje bloqueado al conector ni al conductor.
     */
    public SessionStopResult stopSession(String sessionId, Long driverId, BigDecimal reportedKwh) {
        Objects.requireNonNull(sessionId, "sessionId no puede ser null");
        Objects.requireNonNull(driverId, "driverId no puede ser null");

        ChargingSession session = chargingSessionRepository
                .findBySessionId(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (!session.getDriverId().equals(driverId)) {
            log.warn(
                    "Acceso denegado: el conductor {} quiso detener la sesión {}, que es de otro", driverId, sessionId);
            throw new SessionNotFoundException(sessionId);
        }

        return stopOwnedOrExpiredSession(session, reportedKwh);
    }

    /**
     * Corta automáticamente todas las sesiones activas cuya ventana reservada o ventana walk-in (15
     * minutos antes de la próxima reserva) ya venció (RF10, RF12, ECO-58).
     */
    public int stopExpiredSessions() {
        Instant now = clock.instant();
        List<ChargingSession> activeSessions = chargingSessionRepository.findByStatus(SessionStatus.ACTIVE);
        int stoppedCount = 0;

        for (ChargingSession session : activeSessions) {
            if (session.getBookingId() == null) {
                continue;
            }
            Optional<Booking> booking = bookingRepository.findById(session.getBookingId());
            if (booking.isPresent() && !booking.get().getWindow().end().isAfter(now)) {
                try {
                    stopOwnedOrExpiredSession(session, null);
                    stoppedCount++;
                } catch (RuntimeException failure) {
                    log.warn(
                            "No se pudo cortar automáticamente la sesión vencida {}: {}",
                            session.getSessionId(),
                            failure.getMessage());
                }
            }
        }
        return stoppedCount;
    }

    private SessionStopResult stopOwnedOrExpiredSession(ChargingSession session, BigDecimal reportedKwh) {
        if (!session.isActive()) {
            throw new InvalidBookingRequestException("La sesión de carga no se encuentra activa");
        }

        // 1. Desactivación física y consolidación de energía fuera de @Transactional
        Optional<BigDecimal> chargerKwh = Optional.empty();
        try {
            chargerKwh = physicalChargerPort.deactivate(session.getConnectorId());
        } catch (PhysicalChargerUnavailableException ex) {
            log.warn(
                    "No se pudo desactivar el cargador físico en conector {} al cerrar sesión {}: {}",
                    session.getConnectorId(),
                    session.getSessionId(),
                    ex.getMessage());
        }

        BigDecimal effectiveKwh = reportedKwh != null ? reportedKwh : chargerKwh.orElse(session.getKwhDelivered());
        if (effectiveKwh == null) {
            effectiveKwh = BigDecimal.ZERO;
        }

        // 2. Persistencia transaccional local PRIMERO (sesión COMPLETED, conector AVAILABLE, reserva COMPLETED)
        Instant stoppedAt = clock.instant();
        CompletedSessionData completed =
                transactionalCoordinator.completeLocalSession(session.getSessionId(), effectiveKwh, stoppedAt);

        // 3. Cálculo del costo real y liquidación con descuento de seña (fuera de @Transactional)
        long excessMinutes = 0L;
        if (completed.originalWindowEnd() != null && stoppedAt.isAfter(completed.originalWindowEnd())) {
            excessMinutes =
                    Duration.between(completed.originalWindowEnd(), stoppedAt).toMinutes();
        }

        BigDecimal depositAmount = completed.depositAmount() != null ? completed.depositAmount() : BigDecimal.ZERO;
        BigDecimal realCost;
        BigDecimal finalBilled;
        BigDecimal additionalCharge;
        ConsumptionPaymentStatus paymentStatus;

        try {
            realCost = tariffDirectory.calculateRealCost(
                    session.getConnectorId(),
                    effectiveKwh,
                    session.getPowerLimitKw(),
                    session.getStartedAt(),
                    excessMinutes);
            ChargeSettlement settlement = settlementPolicy.settleCompletedSession(depositAmount, realCost);
            finalBilled = settlement.finalBilledAmount();
            additionalCharge = settlement.amountToCharge();
            if (additionalCharge.signum() > 0) {
                try {
                    paymentStatus = paymentDirectory.chargeConsumption(
                            session.getDriverId(), session.getSessionId(), additionalCharge);
                } catch (PaymentMethodsUnavailableException unavailable) {
                    log.warn(
                            "Pagos no disponible al liquidar sesión {} (monto ${}): queda PENDING_SETTLEMENT",
                            session.getSessionId(),
                            additionalCharge,
                            unavailable);
                    paymentStatus = ConsumptionPaymentStatus.PENDING_SETTLEMENT;
                }
            } else {
                paymentStatus = ConsumptionPaymentStatus.NOT_REQUIRED;
            }
        } catch (PricingUnavailableException unavailable) {
            log.warn(
                    "Tarificación no disponible al liquidar sesión {}: queda PENDING_SETTLEMENT",
                    session.getSessionId(),
                    unavailable);
            realCost = BigDecimal.ZERO;
            finalBilled = depositAmount;
            additionalCharge = BigDecimal.ZERO;
            paymentStatus = ConsumptionPaymentStatus.PENDING_SETTLEMENT;
        }

        ChargingSession closed = completed.session();
        return new SessionStopResult(
                closed.getSessionId(),
                closed.getBookingId(),
                closed.getConnectorId(),
                closed.getStatus(),
                closed.getStartedAt(),
                closed.getStoppedAt(),
                closed.getKwhDelivered(),
                depositAmount,
                realCost,
                finalBilled,
                additionalCharge,
                paymentStatus);
    }
}
