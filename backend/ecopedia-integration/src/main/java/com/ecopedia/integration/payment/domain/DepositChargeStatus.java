package com.ecopedia.integration.payment.domain;

/** En qué quedó un cobro de seña que la pasarela contestó. */
public enum DepositChargeStatus {
    /** Salió la plata. */
    APPROVED,
    /** La pasarela dijo que no: fondos, tarjeta rechazada. No salió nada. */
    DECLINED,
    /** Salió y se devolvió entera. */
    REFUNDED
}
