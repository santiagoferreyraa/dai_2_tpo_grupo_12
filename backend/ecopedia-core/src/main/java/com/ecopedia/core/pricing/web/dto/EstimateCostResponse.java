package com.ecopedia.core.pricing.web.dto;

import com.ecopedia.core.pricing.domain.ChargeBreakdown;
import com.ecopedia.core.pricing.domain.ChargeSpeed;
import java.math.BigDecimal;

/**
 * Lo que costaría una carga (RF06).
 *
 * @param kwhRate el precio del kWh que se aplicaría, con el recargo pico si corresponde.
 * @param peak si la carga empezaría en hora pico en una estación que la cobra.
 * @param settledByDeposit si el estimado no supera la seña: en ese caso la reserva se liquida por la
 *     seña, que es el piso (RF06), y no hay un segundo cobro.
 */
public record EstimateCostResponse(
        Long connectorId,
        BigDecimal estimatedKwh,
        BigDecimal depositAmount,
        BigDecimal estimatedCost,
        BigDecimal kwhRate,
        ChargeSpeed speed,
        boolean peak,
        boolean settledByDeposit) {

    public static EstimateCostResponse from(
            Long connectorId, BigDecimal estimatedKwh, BigDecimal depositAmount, ChargeBreakdown estimate) {
        return new EstimateCostResponse(
                connectorId,
                estimatedKwh,
                depositAmount,
                estimate.total(),
                estimate.kwhRate(),
                estimate.speed(),
                estimate.peak(),
                estimate.total().compareTo(depositAmount) < 0);
    }
}
