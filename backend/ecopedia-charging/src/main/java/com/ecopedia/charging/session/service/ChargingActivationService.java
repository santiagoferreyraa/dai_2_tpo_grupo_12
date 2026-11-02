package com.ecopedia.charging.session.service;

import com.ecopedia.charging.booking.domain.ConnectorNotBookableException;
import com.ecopedia.charging.booking.domain.ConnectorNotFoundException;
import com.ecopedia.charging.checkout.domain.PaymentDirectory;
import com.ecopedia.charging.checkout.domain.PaymentMethodInvalidException;
import com.ecopedia.charging.session.domain.ActivationFailureReason;
import com.ecopedia.charging.session.domain.ActivationRequest;
import com.ecopedia.charging.session.domain.ActivationResult;
import com.ecopedia.charging.session.domain.ChargingPoint;
import com.ecopedia.charging.session.domain.ChargingPointCatalog;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.PhysicalChargerPort;
import com.ecopedia.charging.session.domain.PhysicalChargerRejectedException;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import com.ecopedia.charging.session.domain.PowerDecision;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
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
    private final ChargingSettlementPolicy settlementPolicy;
    private final ChargingTransactionalCoordinator transactionalCoordinator;
    private final PhysicalChargerPort physicalChargerPort;
    private final Clock clock;

    public ChargingActivationService(
            ChargingPointCatalog chargingPointCatalog,
            ChargingPowerPolicy chargingPowerPolicy,
            PaymentDirectory paymentDirectory,
            ChargingSettlementPolicy settlementPolicy,
            ChargingTransactionalCoordinator transactionalCoordinator,
            PhysicalChargerPort physicalChargerPort,
            Clock clock) {
        this.chargingPointCatalog = chargingPointCatalog;
        this.chargingPowerPolicy = chargingPowerPolicy;
        this.paymentDirectory = paymentDirectory;
        this.settlementPolicy = settlementPolicy;
        this.transactionalCoordinator = transactionalCoordinator;
        this.physicalChargerPort = physicalChargerPort;
        this.clock = clock;
    }

    public ActivationResult activate(ActivationRequest request, String authToken) {
        Objects.requireNonNull(request, "request no puede ser null");
        Instant now = clock.instant();

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

        boolean paymentUsable = paymentDirectory.hasUsablePaymentMethod(request.driverId(), authToken);
        if (!paymentUsable) {
            throw new PaymentMethodInvalidException(
                    "El conductor no posee un medio de pago válido o arrastra deuda pendiente");
        }

        // 2. Transacción declarativa local (RNF06): reserva + conector + sesión bajo la misma tx
        String sessionId = UUID.randomUUID().toString();
        ChargingSession session = transactionalCoordinator.persistLocalActivation(
                sessionId,
                request.bookingId(),
                request.connectorId(),
                request.driverId(),
                request.targetBatteryPercent(),
                powerDecision.powerKw(),
                now);

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

            // Compensación de seña (RF13, ECO-59): reintegro íntegro sin penalización
            if (request.bookingId() != null) {
                settlementPolicy.failActivation(request.bookingId(), ActivationFailureReason.CHARGER_UNAVAILABLE);
            }

            throw chargerFailure;
        }

        return ActivationResult.fromSession(session);
    }
}
