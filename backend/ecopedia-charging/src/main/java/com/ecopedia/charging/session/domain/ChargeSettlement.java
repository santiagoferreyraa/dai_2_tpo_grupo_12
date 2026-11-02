package com.ecopedia.charging.session.domain;

import java.math.BigDecimal;

/**
 * Resultado económico de una sesión de carga frente a la seña cobrada al reservar (RF13, RF14,
 * ECO-59).
 *
 * <ul>
 *   <li>En una carga completada normalmente ({@link Outcome#COMPLETED}), la seña funciona como piso
 *       de facturación (RF14): nunca se reintegra diferencia si el consumo fue menor.
 *   <li>Si la carga no puede iniciarse por falla en la habilitación
 *       ({@link Outcome#FAILED_AT_ACTIVATION}), se reintegra la seña entera sin penalización (RF13).
 *   <li>Si la falla ocurre con la carga ya en curso ({@link Outcome#INTERRUPTED_BY_FAULT}), se
 *       liquida el consumo real sin aplicar el piso de la seña y se reintegra la diferencia si el
 *       consumo real no alcanzó la seña (RF13).
 * </ul>
 *
 * @param depositAmount importe de la seña ya cobrada al confirmar la reserva (0 si no hubo seña)
 * @param realCost importe del consumo real más cargos por minuto devengados
 * @param finalBilledAmount importe total que termina pagando el conductor por la sesión
 * @param amountToCharge saldo adicional a cobrar al finalizar cuando el consumo supera la seña
 * @param amountToRefund importe a reintegrar sobre la seña cuando corresponde por RF13
 * @param outcome motivo bajo el cual se liquidó la sesión
 */
public record ChargeSettlement(
        BigDecimal depositAmount,
        BigDecimal realCost,
        BigDecimal finalBilledAmount,
        BigDecimal amountToCharge,
        BigDecimal amountToRefund,
        Outcome outcome) {

    public enum Outcome {
        /** Carga finalizada normalmente: la seña opera como piso de facturación (RF14). */
        COMPLETED,
        /** Falla al habilitar el conector: reserva liberada y reintegro total de la seña (RF13). */
        FAILED_AT_ACTIVATION,
        /** Falla con la carga en curso: liquidación del consumo real sin piso de la seña (RF13). */
        INTERRUPTED_BY_FAULT
    }

    public boolean requiresRefund() {
        return amountToRefund.signum() > 0;
    }

    public boolean requiresAdditionalCharge() {
        return amountToCharge.signum() > 0;
    }
}
