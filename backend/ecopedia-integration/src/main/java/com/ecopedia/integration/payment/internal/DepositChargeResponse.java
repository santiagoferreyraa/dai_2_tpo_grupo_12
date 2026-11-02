package com.ecopedia.integration.payment.internal;

import com.ecopedia.integration.payment.domain.CardBrand;
import com.ecopedia.integration.payment.domain.DepositCharge;
import com.ecopedia.integration.payment.domain.DepositChargeStatus;
import java.math.BigDecimal;

/** Un cobro de seña, como lo ve el checkout: el id para devolverlo y la tarjeta para nombrarla. */
public record DepositChargeResponse(
        Long chargeId, DepositChargeStatus status, BigDecimal amount, CardBrand cardBrand, String cardLastFour) {

    static DepositChargeResponse from(DepositCharge charge) {
        return new DepositChargeResponse(
                charge.getId(),
                charge.getStatus(),
                charge.getAmount(),
                charge.getCardBrand(),
                charge.getCardLastFour());
    }
}
