package com.ecopedia.charging.session.service;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingNotFoundException;
import com.ecopedia.charging.booking.domain.BookingRepository;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.booking.domain.BookingStateConflictException;
import com.ecopedia.charging.booking.domain.BookingStatus;
import com.ecopedia.charging.booking.domain.ConnectorNotBookableException;
import com.ecopedia.charging.booking.domain.ConnectorNotFoundException;
import com.ecopedia.charging.booking.domain.InvalidBookingRequestException;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.checkout.domain.ConsumptionPaymentStatus;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.PaymentMethodsUnavailableException;
import com.ecopedia.charging.checkout.domain.PricingUnavailableException;
import com.ecopedia.charging.checkout.domain.TariffDirectory;
import com.ecopedia.charging.session.domain.AccessCodeDirectory;
import com.ecopedia.charging.session.domain.ActivationFailureReason;
import com.ecopedia.charging.session.domain.ActivationRequest;
import com.ecopedia.charging.session.domain.ActivationResult;
import com.ecopedia.charging.session.domain.ChargeSettlement;
import com.ecopedia.charging.session.domain.ChargerEndReason;
import com.ecopedia.charging.session.domain.ChargingPoint;
import com.ecopedia.charging.session.domain.ChargingPointCatalog;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.ChargingSessionService;
import com.ecopedia.charging.session.domain.ChargingSessionView;
import com.ecopedia.charging.session.domain.CutoffDecision;
import com.ecopedia.charging.session.domain.DriverAlreadyChargingException;
import com.ecopedia.charging.session.domain.InvalidAccessCodeException;
import com.ecopedia.charging.session.domain.PhysicalChargerPort;
import com.ecopedia.charging.session.domain.PhysicalChargerRejectedException;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import com.ecopedia.charging.session.domain.PowerDecision;
import com.ecopedia.charging.session.domain.SessionNotFoundException;
import com.ecopedia.charging.session.domain.SessionStatus;
import com.ecopedia.charging.session.domain.SessionStopReason;
import com.ecopedia.charging.session.domain.SessionStopResult;
import com.ecopedia.charging.session.domain.TelemetryReading;
import com.ecopedia.charging.session.domain.TerminalAccessCode;
import com.ecopedia.charging.session.service.ChargingTransactionalCoordinator.CompletedSessionData;
import com.ecopedia.charging.session.service.ChargingTransactionalCoordinator.SessionAlreadyClosedException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Orquestador del flujo de habilitación de carga (Flujo B, RF11, RNF06, ECO-44, ECO-60).
 *
 * <p>Secuencia de habilitación, <b>escanear → validar → activar</b>:
 * <ol>
 *   <li><b>Escanear (ECO-44):</b> el código de la terminal —PIN o QR— dice qué conector es, y con
 *       eso acredita que el conductor está frente a él. Un código errado cuenta para el tope de
 *       {@link AccessCodeAttemptLimiter}. Con el conector resuelto: que el conductor no esté ya
 *       cargando, y qué reserva usa (la que trae, o la suya para ese conector si ya se puede usar).
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
public class ChargingSessionServiceImpl implements ChargingSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChargingSessionServiceImpl.class);

    private final AccessCodeDirectory accessCodeDirectory;
    private final AccessCodeAttemptLimiter attemptLimiter;
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
    private final Duration earlyActivation;

    public ChargingSessionServiceImpl(
            AccessCodeDirectory accessCodeDirectory,
            AccessCodeAttemptLimiter attemptLimiter,
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
            Clock clock,
            @Value("${ecopedia.booking.early-activation:PT15M}") Duration earlyActivation) {
        this.accessCodeDirectory = accessCodeDirectory;
        this.attemptLimiter = attemptLimiter;
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
        this.earlyActivation = earlyActivation;
    }

    @Override
    public ActivationResult activate(ActivationRequest request, String authToken) {
        Objects.requireNonNull(request, "request no puede ser null");
        Instant now = clock.instant();

        // 0. Escanear: el código dice qué conector es y acredita la presencia (ECO-44)
        Long connectorId = resolveConnector(request);

        chargingSessionRepository.findActiveByDriverId(request.driverId()).ifPresent(active -> {
            throw new DriverAlreadyChargingException(request.driverId(), active.getSessionId());
        });

        // Sin reserva elegida: si tiene una para este conector que ya se puede usar, es esa (ECO-44)
        Long bookingId = request.bookingId() != null
                ? request.bookingId()
                : bookingService
                        .findChargeableBooking(request.driverId(), connectorId, now, earlyActivation)
                        .map(Booking::getId)
                        .orElse(null);

        // Verificación previa de la reserva (pertenencia, conector y estado) antes de llamadas externas
        if (bookingId != null) {
            Booking booking = bookingService.getBooking(bookingId);
            if (!booking.belongsTo(request.driverId())) {
                log.warn(
                        "Acceso denegado: el conductor {} quiso habilitar con la reserva {}, que es de otro",
                        request.driverId(),
                        bookingId);
                throw new BookingNotFoundException(bookingId);
            }
            if (!booking.getConnectorId().equals(connectorId)) {
                throw new InvalidBookingRequestException("La reserva es de otro conector");
            }
            if (!booking.isConfirmed()) {
                // Antes de llamar a nadie, el mismo 409 que daría la propia reserva (patrón State).
                throw new BookingStateConflictException(
                        bookingId, "en " + booking.getStatus(), "la habilitación de la carga");
            }
            // Desde 15 minutos antes del turno y hasta su fin (Manuel, 12/11). La reserva lo vuelve
            // a comprobar en la transacción; acá es para no consultar a nadie si ya se sabe que no.
            booking.checkActivationWindow(now, earlyActivation);
            rejectIfConnectorTakenBefore(booking, now);
        }

        // 1. Validaciones y consultas externas previas (FUERA de la transacción declarativa)
        ChargingPoint chargingPoint =
                chargingPointCatalog.find(connectorId).orElseThrow(() -> new ConnectorNotFoundException(connectorId));

        PowerDecision powerDecision = chargingPowerPolicy.decide(chargingPoint);
        if (!powerDecision.canStart()) {
            if (bookingId != null) {
                settlementPolicy.failActivation(bookingId, ActivationFailureReason.REJECTED_BY_GRID);
            }
            throw new ConnectorNotBookableException(
                    connectorId, "La distribuidora eléctrica rechazó la habilitación de la demanda");
        }

        // Validación síncrona completa contra la pasarela y deudas (RF11, RF14, ECO-54)
        paymentDirectory.validatePaymentMethodForCharging(request.driverId(), null);

        // Registro de reserva instantánea si es carga sin reserva previa (RF10, ECO-58)
        Long effectiveBookingId = bookingId;
        if (effectiveBookingId == null) {
            Booking walkInBooking = bookingService.registerWalkIn(connectorId, request.driverId());
            effectiveBookingId = walkInBooking.getId();
        }

        // 2. Transacción declarativa local (RNF06): reserva + conector + sesión bajo la misma tx
        String sessionId = UUID.randomUUID().toString();
        ChargingSession session;
        try {
            session = transactionalCoordinator.persistLocalActivation(
                    sessionId,
                    effectiveBookingId,
                    connectorId,
                    request.driverId(),
                    request.targetBatteryPercent(),
                    powerDecision.powerKw(),
                    now);
        } catch (RuntimeException persistFailure) {
            if (bookingId == null && effectiveBookingId != null) {
                settlementPolicy.failActivation(effectiveBookingId, ActivationFailureReason.CHARGER_UNAVAILABLE);
            }
            throw persistFailure;
        }

        // 3. Activación física del hardware externo (NO participa del rollback de base de datos)
        try {
            physicalChargerPort.activate(connectorId, sessionId, powerDecision.powerKw());
        } catch (PhysicalChargerRejectedException | PhysicalChargerUnavailableException chargerFailure) {
            log.error(
                    "Falla en el cargador físico al activar el conector {}: aplicando compensación RNF06 / RF13",
                    connectorId,
                    chargerFailure);

            // Compensación local: marcar sesión FAULTED y liberar conector local
            transactionalCoordinator.compensateLocalFailure(connectorId, sessionId, clock.instant());

            // Compensación de seña (RF13, ECO-59) o liberación de reserva walk-in (ECO-58)
            if (effectiveBookingId != null) {
                settlementPolicy.failActivation(effectiveBookingId, ActivationFailureReason.CHARGER_UNAVAILABLE);
            }

            throw chargerFailure;
        }

        return ActivationResult.fromSession(session);
    }

    /**
     * El conector que abre el código escaneado o tipeado (RF11, ECO-44).
     *
     * <p>Los tres rechazos —no tiene la forma de un código, no es de ningún conector, es de otro que
     * el que dijo el conductor— cuentan para el tope de intentos y salen con el mismo motivo: ver
     * {@link InvalidAccessCodeException}. El acierto borra la cuenta.
     */
    private Long resolveConnector(ActivationRequest request) {
        attemptLimiter.checkAllowed(request.driverId());

        String code = TerminalAccessCode.parse(request.pinOrQr()).orElse(null);
        if (code == null) {
            attemptLimiter.recordFailure(request.driverId());
            throw new InvalidAccessCodeException("El código no tiene la forma de un PIN ni del QR de una terminal");
        }

        Long connectorId = accessCodeDirectory.findConnectorId(code).orElse(null);
        if (connectorId == null) {
            attemptLimiter.recordFailure(request.driverId());
            throw new InvalidAccessCodeException("Ningún conector tiene ese código");
        }
        if (request.connectorId() != null && !request.connectorId().equals(connectorId)) {
            attemptLimiter.recordFailure(request.driverId());
            log.info(
                    "El conductor {} escaneó el conector {} pero pidió habilitar el {}",
                    request.driverId(),
                    connectorId,
                    request.connectorId());
            throw new InvalidAccessCodeException("El código es de otro conector");
        }

        attemptLimiter.recordSuccess(request.driverId());
        return connectorId;
    }

    /**
     * Llegar antes del turno vale solo con la terminal libre (Manuel, 12/11), y libre no es solo
     * "sin carga en curso" —eso lo controla la transacción con el estado del conector—: tampoco
     * puede haber otra reserva confirmada cuyo turno esté corriendo, aunque su conductor todavía no
     * haya enchufado. Si no, se le tomaría la terminal en su propio turno.
     */
    private void rejectIfConnectorTakenBefore(Booking booking, Instant now) {
        Instant start = booking.getWindow().start();
        if (now.isBefore(start)
                && bookingRepository.existsOverlapping(booking.getConnectorId(), BookingStatus.CONFIRMED, now, start)) {
            log.info(
                    "Habilitación anticipada rechazada: el conector {} tiene otra reserva en curso antes de la {}",
                    booking.getConnectorId(),
                    booking.getId());
            throw new SlotUnavailableException(booking.getConnectorId());
        }
    }

    /**
     * Detiene una sesión de carga iniciada por el conductor (Flujo C, RF12, RF14, ECO-55).
     *
     * <p>Libera primero el cargador físico y la transacción local en base de datos, para garantizar
     * que una caída de Tarificación o Pagos nunca deje bloqueado al conector ni al conductor.
     */
    @Override
    public SessionStopResult stopSession(String sessionId, Long driverId) {
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

        return stopOwnedOrExpiredSession(session, SessionStopReason.DRIVER_STOPPED);
    }

    /**
     * Corta automáticamente todas las sesiones activas cuya ventana reservada o ventana walk-in (15
     * minutos antes de la próxima reserva) ya venció (RF10, RF12, ECO-58).
     */
    @Override
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
                    stopOwnedOrExpiredSession(session, SessionStopReason.WINDOW_ENDED);
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

    private SessionStopResult stopOwnedOrExpiredSession(ChargingSession session, SessionStopReason reason) {
        if (!session.isActive()) {
            // Ya cerró —un reintento, o el corte automático llegó primero—: se devuelve lo que quedó.
            return stopResultOf(session);
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

        // Los kWh salen del cargador; si no contestó, de la última lectura de la telemetría (ECO-44).
        BigDecimal effectiveKwh = chargerKwh.orElse(session.getKwhDelivered());
        if (effectiveKwh == null) {
            effectiveKwh = BigDecimal.ZERO;
        }

        // 2. Persistencia transaccional local PRIMERO (sesión COMPLETED, conector AVAILABLE, reserva COMPLETED)
        Instant stoppedAt = clock.instant();
        CompletedSessionData completed;
        try {
            completed = transactionalCoordinator.completeLocalSession(session.getSessionId(), effectiveKwh, stoppedAt);
        } catch (OptimisticLockingFailureException | SessionAlreadyClosedException closedMeanwhile) {
            /*
             * Otro cierre —el corte automático, un doble clic— se cruzó con este y confirmó primero. El
             * bloqueo optimista de la sesión es el que lo detecta; la liquidación es de ese otro
             * cierre, y esta no se repite.
             */
            log.info("La sesión {} se cerró por otro camino mientras se la detenía", session.getSessionId());
            return chargingSessionRepository
                    .findBySessionId(session.getSessionId())
                    .map(this::stopResultOf)
                    .orElseThrow(() -> new SessionNotFoundException(session.getSessionId()));
        }

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

        // 4. La liquidación queda guardada en la sesión, para devolverla igual si se repite el pedido
        ChargingSession settled = transactionalCoordinator.recordSettlement(
                session.getSessionId(), reason, depositAmount, realCost, finalBilled, additionalCharge, paymentStatus);
        return stopResultOf(settled);
    }

    private SessionStopResult stopResultOf(ChargingSession session) {
        return new SessionStopResult(
                session.getSessionId(),
                session.getBookingId(),
                session.getConnectorId(),
                session.getStatus(),
                session.getStartedAt(),
                session.getStoppedAt(),
                session.getKwhDelivered(),
                session.getDepositApplied(),
                session.getRealCost(),
                session.getFinalBilled(),
                session.getAdditionalCharge(),
                session.getPaymentStatus(),
                session.getStopReason());
    }

    @Override
    public ChargingSessionView getSession(String sessionId, Long driverId) {
        return chargingSessionRepository
                .findBySessionId(sessionId)
                .filter(session -> session.belongsTo(driverId))
                .map(ChargingSessionView::of)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    @Override
    public Optional<ChargingSessionView> findActiveSession(Long driverId) {
        return chargingSessionRepository.findActiveByDriverId(driverId).map(ChargingSessionView::of);
    }

    @Override
    public boolean processTelemetry(TelemetryReading reading) {
        Objects.requireNonNull(reading, "reading no puede ser null");
        if (reading.sessionId() == null
                || reading.sessionId().isBlank()
                || reading.timestamp() == null
                || reading.energyKwh() == null
                || reading.energyKwh().signum() < 0) {
            throw new IllegalArgumentException("La lectura " + reading.eventId() + " no trae sesión, instante o kWh");
        }

        // Solo la última lectura trae el motivo; si el cargador no lo manda, se deduce del tipo.
        ChargerEndReason endReason = null;
        if (reading.isFinal()) {
            if (reading.endReason() != null) {
                endReason = reading.endReason();
            } else if (reading.type() == TelemetryReading.Type.FAULT) {
                endReason = ChargerEndReason.FAULT;
            } else {
                endReason = ChargerEndReason.STOPPED;
            }
        }

        int updated = chargingSessionRepository.recordReading(
                reading.sessionId(),
                reading.energyKwh(),
                reading.powerKw(),
                reading.batteryPercent(),
                reading.timestamp().truncatedTo(ChronoUnit.MICROS),
                endReason);

        if (updated == 0) {
            // Lo normal al detener: la última lectura del cargador llega con la sesión ya cerrada.
            log.debug(
                    "Lectura {} descartada: la sesión {} no está activa o ya tiene una lectura posterior",
                    reading.eventId(),
                    reading.sessionId());
            return false;
        }

        if (endReason == ChargerEndReason.FAULT) {
            // Una falla con la carga en curso es RF13: se liquida sin el piso de la seña, y no es este
            // corte el que la cierra. Queda registrada y la sesión, abierta.
            log.warn(
                    "El cargador falló con la sesión {} del conector {} en curso ({} kWh): quedó registrado y"
                            + " la sesión sigue abierta hasta que se la cierre",
                    reading.sessionId(),
                    reading.connectorId(),
                    reading.energyKwh());
            return true;
        }

        cutOffIfDue(reading, endReason);
        return true;
    }

    /**
     * El corte automático (RF12, ECO-45): con la lectura recién guardada, si la batería llegó al
     * objetivo o la ventana terminó, la sesión se cierra por el mismo camino que cuando la detiene el
     * conductor —apagar, cerrar, liquidar, cobrar—. Ver {@link CutoffDecision}.
     *
     * <p>Que el cargador avise que la batería se llenó también corta, aunque la lectura no traiga el
     * porcentaje: lleno es más que cualquier objetivo.
     *
     * <p><b>Un error acá no devuelve la lectura al broker.</b> La lectura ya quedó guardada; si el corte
     * falla, la próxima lectura —a los pocos segundos— lo vuelve a intentar, y el fin de ventana lo
     * cubre además el barrido. Reentregarla no arreglaría nada y llenaría la cola de reintentos. Si
     * otro cierre llega a la vez —el conductor, el barrido—, el cierre único ya resuelve que se liquide
     * una sola vez (ECO-44).
     */
    private void cutOffIfDue(TelemetryReading reading, ChargerEndReason endReason) {
        try {
            Optional<ChargingSession> found = chargingSessionRepository.findBySessionId(reading.sessionId());
            if (found.isEmpty() || !found.get().isActive()) {
                return;
            }
            ChargingSession session = found.get();

            Instant windowEnd = session.getBookingId() == null
                    ? null
                    : bookingRepository
                            .findById(session.getBookingId())
                            .map(booking -> booking.getWindow().end())
                            .orElse(null);
            BigDecimal battery = endReason == ChargerEndReason.BATTERY_FULL && reading.batteryPercent() == null
                    ? BigDecimal.valueOf(100)
                    : reading.batteryPercent();

            Optional<SessionStopReason> cut =
                    CutoffDecision.evaluate(battery, session.getTargetBatteryPercent(), reading.timestamp(), windowEnd);
            if (cut.isEmpty()) {
                return;
            }

            log.info(
                    "Corte automático de la sesión {} del conector {}: {} ({}% de {}%, ventana hasta {})",
                    session.getSessionId(),
                    session.getConnectorId(),
                    cut.get(),
                    battery,
                    session.getTargetBatteryPercent(),
                    windowEnd);
            stopOwnedOrExpiredSession(session, cut.get());
        } catch (RuntimeException failure) {
            log.warn(
                    "No se pudo cortar automáticamente la sesión {} con la lectura {}: {}. Lo reintenta la próxima"
                            + " lectura",
                    reading.sessionId(),
                    reading.eventId(),
                    failure.getMessage());
        }
    }
}
