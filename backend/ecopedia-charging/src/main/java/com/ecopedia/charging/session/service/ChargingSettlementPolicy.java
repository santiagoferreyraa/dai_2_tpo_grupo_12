package com.ecopedia.charging.session.service;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.booking.domain.ConnectorCatalog;
import com.ecopedia.charging.booking.domain.ConnectorNotFoundException;
import com.ecopedia.charging.booking.domain.ConnectorSnapshot;
import com.ecopedia.charging.session.domain.ActivationFailureReason;
import com.ecopedia.charging.session.domain.ChargeSettlement;
import com.ecopedia.charging.session.domain.ChargeSettlement.Outcome;
import com.ecopedia.charging.session.domain.PowerDecision;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Regla de liquidación y de falla en la habilitación de {@code SesionesDeCarga} (RF13, RF14,
 * ECO-59).
 *
 * <p>Concentra las tres reglas económicas que distinguen una falla del operador de una sesión
 * normal:
 *
 * <ol>
 *   <li><b>Falla antes de arrancar</b> (conector fuera de servicio, cargador que no responde o
 *       rechazo de la distribuidora con tope 0): se libera la reserva sin penalización y se
 *       reintegra el 100% de la seña, aunque ya haya pasado el plazo de preaviso de 5 horas.
 *   <li><b>Falla con la carga en curso</b>: se liquida únicamente el consumo real sin aplicar el
 *       piso de la seña. Si el consumo real es menor que la seña, se reintegra la diferencia.
 *   <li><b>Carga completada sin falla</b> (RF14): la seña opera como piso de facturación; si el
 *       consumo real es menor que la seña, no hay reintegro.
 * </ol>
 */
@Service
public class ChargingSettlementPolicy {

    private static final Logger log = LoggerFactory.getLogger(ChargingSettlementPolicy.class);

    private final BookingService bookingService;
    private final ConnectorCatalog connectorCatalog;

    public ChargingSettlementPolicy(BookingService bookingService, ConnectorCatalog connectorCatalog) {
        this.bookingService = bookingService;
        this.connectorCatalog = connectorCatalog;
    }

    /**
     * Evalúa si el conector y la decisión de potencia permiten habilitar la carga o si se configura
     * una falla en la habilitación de RF13.
     *
     * <p>Una reducción de potencia por la distribuidora ({@code LIMITED_BY_GRID}) o la caída del
     * servicio de red eléctrica ({@code GRID_UNAVAILABLE}) <b>no</b> son fallas de habilitación: la
     * carga arranca a potencia reducida o normal (ECO-52).
     */
    public Optional<ActivationFailureReason> checkActivation(Long connectorId, PowerDecision powerDecision) {
        ConnectorSnapshot connector = connectorCatalog
                .findConnector(connectorId)
                .orElseThrow(() -> new ConnectorNotFoundException(connectorId));
        return checkActivation(connector, powerDecision);
    }

    /** Evalúa la disponibilidad física del conector y la factibilidad eléctrica ya consultadas. */
    public Optional<ActivationFailureReason> checkActivation(ConnectorSnapshot connector, PowerDecision powerDecision) {
        Objects.requireNonNull(connector, "connector no puede ser null");
        Objects.requireNonNull(powerDecision, "powerDecision no puede ser null");
        if (connector.isOutOfService()) {
            return Optional.of(ActivationFailureReason.CONNECTOR_OUT_OF_SERVICE);
        }
        if (!powerDecision.canStart()) {
            return Optional.of(ActivationFailureReason.REJECTED_BY_GRID);
        }
        return Optional.empty();
    }

    /**
     * Aplica una falla en la habilitación sobre la reserva (RF13, ECO-59): libera la ventana sin
     * penalización y deja encolado el reintegro total de la seña.
     */
    public ChargeSettlement failActivation(Long bookingId, ActivationFailureReason reason) {
        Objects.requireNonNull(reason, "reason no puede ser null");
        Booking released = bookingService.releaseForActivationFailure(bookingId);
        BigDecimal deposit = nonNegative(released.getDepositAmount());
        BigDecimal refund = nonNegative(released.getDepositRefundAmount());
        log.info(
                "Falla en la habilitación de la reserva {} por {}: se libera sin penalización y se reintegra ${}",
                bookingId,
                reason,
                refund);
        return new ChargeSettlement(
                deposit, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, refund, Outcome.FAILED_AT_ACTIVATION);
    }

    /**
     * Liquida una sesión interrumpida por una falla con la carga ya en curso (RF13, ECO-59): se
     * cobra el consumo real sin aplicar el piso de la seña y se reintegra la diferencia cuando el
     * consumo real fue menor que la seña.
     */
    public ChargeSettlement settleFaultedSession(Long bookingId, BigDecimal realCost) {
        BigDecimal cost = requireNonNegative(realCost);
        Booking settled = bookingService.settleFaultedBooking(bookingId, cost);
        BigDecimal deposit = nonNegative(settled.getDepositAmount());
        if (cost.signum() == 0) {
            return new ChargeSettlement(
                    deposit,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    nonNegative(settled.getDepositRefundAmount()),
                    Outcome.FAILED_AT_ACTIVATION);
        }
        BigDecimal refund = nonNegative(settled.getDepositRefundAmount());
        BigDecimal additionalCharge = cost.compareTo(deposit) > 0 ? cost.subtract(deposit) : BigDecimal.ZERO;
        log.info(
                "Liquidación por falla en curso de la reserva {}: consumo real ${}, seña ${}, a reintegrar ${}, a cobrar ${}",
                bookingId,
                cost,
                deposit,
                refund,
                additionalCharge);
        return new ChargeSettlement(deposit, cost, cost, additionalCharge, refund, Outcome.INTERRUPTED_BY_FAULT);
    }

    /**
     * Liquida una sesión completada normalmente (RF14): la seña opera como piso de facturación y
     * nunca se reintegra diferencia si el consumo real fue menor.
     */
    public ChargeSettlement settleCompletedSession(BigDecimal depositAmount, BigDecimal realCost) {
        BigDecimal deposit = requireNonNegative(depositAmount);
        BigDecimal cost = requireNonNegative(realCost);
        BigDecimal finalBilled = cost.max(deposit);
        BigDecimal additionalCharge = cost.compareTo(deposit) > 0 ? cost.subtract(deposit) : BigDecimal.ZERO;
        return new ChargeSettlement(deposit, cost, finalBilled, additionalCharge, BigDecimal.ZERO, Outcome.COMPLETED);
    }

    private static BigDecimal requireNonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("El importe no puede ser negativo");
        }
        return value;
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() <= 0 ? BigDecimal.ZERO : value;
    }
}
