package com.ecopedia.charging.checkout.domain;

/**
 * Estado del cobro de consumo adicional al finalizar una sesión de carga (RF14, RNF06, ECO-55).
 */
public enum ConsumptionPaymentStatus {
    /** La seña cubrió el consumo total o el costo real fue cero: no hubo cobro adicional. */
    NOT_REQUIRED,
    /** El saldo adicional de consumo fue cobrado exitosamente en la pasarela. */
    CHARGED,
    /** El cobro falló o fue rechazado y quedó registrado como deuda pendiente en Pagos. */
    DEBT_RECORDED,
    /** Tarificación o Pagos no respondieron al momento del cierre; la sesión se cerró igual. */
    PENDING_SETTLEMENT
}
