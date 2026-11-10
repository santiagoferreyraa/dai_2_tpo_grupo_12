package com.ecopedia.charging.session.domain;

import java.math.BigDecimal;

/**
 * Puerto hacia Pagos (ecopedia-integration): el cobro del consumo al cerrar la sesión (RF14, RNF06,
 * ECO-44).
 */
public interface ConsumptionPayments {

    /**
     * Cobra el importe en la tarjeta del conductor. Si la pasarela lo rechaza o no contesta, Pagos lo
     * anota como deuda pendiente y contesta igual: un rechazo es una respuesta, no un error.
     *
     * @param reference la sesión; también es la clave de idempotencia, así que repetir el pedido no
     *     cobra dos veces
     * @return {@link SessionPaymentStatus#CHARGED} o {@link SessionPaymentStatus#DEBT_REGISTERED}
     * @throws ConsumptionPaymentUnavailableException si Pagos mismo no contestó
     */
    SessionPaymentStatus charge(Long driverId, BigDecimal amount, String reference);
}
