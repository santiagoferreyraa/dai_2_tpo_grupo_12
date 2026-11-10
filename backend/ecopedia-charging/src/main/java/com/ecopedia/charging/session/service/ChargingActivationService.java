package com.ecopedia.charging.session.service;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingNotFoundException;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.booking.domain.ConnectorNotBookableException;
import com.ecopedia.charging.booking.domain.ConnectorNotFoundException;
import com.ecopedia.charging.booking.domain.InvalidBookingRequestException;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.session.domain.AccessCodeDirectory;
import com.ecopedia.charging.session.domain.ActivationFailureReason;
import com.ecopedia.charging.session.domain.ActivationRequest;
import com.ecopedia.charging.session.domain.ActivationResult;
import com.ecopedia.charging.session.domain.ChargingPoint;
import com.ecopedia.charging.session.domain.ChargingPointCatalog;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.DriverAlreadyChargingException;
import com.ecopedia.charging.session.domain.InvalidAccessCodeException;
import com.ecopedia.charging.session.domain.PhysicalChargerPort;
import com.ecopedia.charging.session.domain.PhysicalChargerRejectedException;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import com.ecopedia.charging.session.domain.PowerDecision;
import com.ecopedia.charging.session.domain.TerminalAccessCode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orquestador del flujo de habilitación de carga (Flujo B, RF11, RNF06, ECO-44, ECO-60).
 *
 * <p>Secuencia de habilitación, <b>escanear → validar → activar</b>:
 * <ol>
 *   <li><b>Escanear (ECO-44):</b> el código de la terminal —PIN o QR— dice qué conector es, y con
 *       eso acredita que el conductor está frente a él. Un código errado cuenta para el tope de
 *       {@link AccessCodeAttemptLimiter}. Con el conector resuelto: que el conductor no esté ya
 *       cargando, y qué reserva usa (la que trae, o la suya para ese conector y en hora).
 *   <li><b>Validar:</b> consulta de catálogo de terminales, factibilidad eléctrica a la distribuidora
 *       (SOAP) y verificación de tarjeta cobrable sin deuda (REST). Ninguna mueve dinero ni participa
 *       de la transacción local: el consumo se cobra al cerrar la sesión (RF14).
 *   <li><b>Transacción declarativa local:</b> persistencia atómica de la reserva ({@code IN_PROGRESS}),
 *       el conector ({@code OCCUPIED}) y la sesión ({@code ACTIVE}) vía {@link ChargingTransactionalCoordinator}.
 *   <li><b>Activar:</b> orden de encendido al cargador por {@link PhysicalChargerPort}.
 *       Si el hardware no responde o rechaza la orden, el sistema no hace rollback de operaciones
 *       externas sino una <b>compensación explícita</b>: libera el conector, marca la sesión como
 *       {@code FAULTED} y encola el reintegro del 100% de la seña sin penalización (RF13, ECO-59).
 * </ol>
 */
@Service
public class ChargingActivationService {

    private static final Logger log = LoggerFactory.getLogger(ChargingActivationService.class);

    private final AccessCodeDirectory accessCodeDirectory;
    private final AccessCodeAttemptLimiter attemptLimiter;
    private final ChargingSessionRepository sessionRepository;
    private final ChargingPointCatalog chargingPointCatalog;
    private final ChargingPowerPolicy chargingPowerPolicy;
    private final PaymentDirectory paymentDirectory;
    private final BookingService bookingService;
    private final ChargingSettlementPolicy settlementPolicy;
    private final ChargingTransactionalCoordinator transactionalCoordinator;
    private final PhysicalChargerPort physicalChargerPort;
    private final Clock clock;

    public ChargingActivationService(
            AccessCodeDirectory accessCodeDirectory,
            AccessCodeAttemptLimiter attemptLimiter,
            ChargingSessionRepository sessionRepository,
            ChargingPointCatalog chargingPointCatalog,
            ChargingPowerPolicy chargingPowerPolicy,
            PaymentDirectory paymentDirectory,
            BookingService bookingService,
            ChargingSettlementPolicy settlementPolicy,
            ChargingTransactionalCoordinator transactionalCoordinator,
            PhysicalChargerPort physicalChargerPort,
            Clock clock) {
        this.accessCodeDirectory = accessCodeDirectory;
        this.attemptLimiter = attemptLimiter;
        this.sessionRepository = sessionRepository;
        this.chargingPointCatalog = chargingPointCatalog;
        this.chargingPowerPolicy = chargingPowerPolicy;
        this.paymentDirectory = paymentDirectory;
        this.bookingService = bookingService;
        this.settlementPolicy = settlementPolicy;
        this.transactionalCoordinator = transactionalCoordinator;
        this.physicalChargerPort = physicalChargerPort;
        this.clock = clock;
    }

    public ActivationResult activate(ActivationRequest request, String authToken) {
        Objects.requireNonNull(request, "request no puede ser null");
        Instant now = clock.instant();

        // 0. Escanear: el código dice qué conector es y acredita la presencia (ECO-44)
        Long connectorId = resolveConnector(request);

        sessionRepository.findActiveByDriverId(request.driverId()).ifPresent(active -> {
            throw new DriverAlreadyChargingException(request.driverId(), active.getSessionId());
        });

        Long bookingId = request.bookingId();
        if (bookingId != null) {
            // Verificación de la reserva que eligió el conductor, antes de cualquier llamada externa
            requireUsableBooking(bookingService.getBooking(bookingId), request.driverId(), connectorId, now);
        } else {
            // Sin reserva elegida: si tiene una para este conector y es la hora, es esa (ECO-44)
            bookingId = bookingService
                    .findChargeableBooking(request.driverId(), connectorId, now)
                    .map(Booking::getId)
                    .orElse(null);
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
        boolean walkIn = bookingId == null;
        Long effectiveBookingId = bookingId;
        if (walkIn) {
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
            if (walkIn && effectiveBookingId != null) {
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
     * Que la reserva elegida sirva para cargar acá y ahora: del conductor, de este conector,
     * confirmada y con la ventana corriendo.
     */
    private static void requireUsableBooking(Booking booking, Long driverId, Long connectorId, Instant now) {
        if (!booking.belongsTo(driverId)) {
            log.warn(
                    "Acceso denegado: el conductor {} quiso habilitar con la reserva {}, que es de otro",
                    driverId,
                    booking.getId());
            throw new BookingNotFoundException(booking.getId());
        }
        if (!booking.getConnectorId().equals(connectorId)) {
            throw new InvalidBookingRequestException("La reserva es de otro conector");
        }
        if (!booking.isConfirmed()) {
            throw new InvalidBookingRequestException("La reserva " + booking.getId() + " no está confirmada");
        }
        if (now.isBefore(booking.getWindow().start())) {
            throw new InvalidBookingRequestException(
                    "La reserva todavía no empezó: la carga se puede iniciar desde el comienzo de la ventana");
        }
        if (!now.isBefore(booking.getWindow().end())) {
            throw new InvalidBookingRequestException("La ventana de la reserva ya terminó");
        }
    }
}
