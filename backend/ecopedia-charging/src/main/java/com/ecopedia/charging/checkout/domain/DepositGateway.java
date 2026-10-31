package com.ecopedia.charging.checkout.domain;

import java.math.BigDecimal;

/**
 * Puerto secundario hacia Pagos (ecopedia-integration) para cobrar y devolver la seña (ECO-53).
 *
 * <p>Va por la ruta interna de Pagos con la clave de servicio, no con el token del conductor: el
 * importe lo decide Tarificación y lo trae el checkout, y con una ruta del conductor cualquiera
 * podría pedir que le cobren lo que quisiera (decidido por Manuel el 31/10).
 */
public interface DepositGateway {

    /**
     * Cobra la seña.
     *
     * @param paymentMethodId la tarjeta elegida, o {@code null} para la más reciente que sirva
     * @param idempotencyKey la misma en cada reintento del mismo cobro
     * @throws CardDeclinedException si la pasarela rechazó el cobro
     * @throws PaymentMethodInvalidException si no hay tarjeta con la que cobrar
     * @throws IdempotencyKeyReusedException si la clave ya nombra otro cobro
     * @throws DepositPaymentUnavailableException si Pagos o la pasarela no contestaron
     */
    DepositCharge charge(
            Long driverId, Long paymentMethodId, BigDecimal amount, String reference, String idempotencyKey);

    /**
     * Devuelve entera la seña de ese cobro. Idempotente.
     *
     * @throws DepositRefundRefusedException si Pagos dice que no se puede devolver
     * @throws DepositPaymentUnavailableException si Pagos o la pasarela no contestaron
     */
    void refund(Long chargeId);
}
