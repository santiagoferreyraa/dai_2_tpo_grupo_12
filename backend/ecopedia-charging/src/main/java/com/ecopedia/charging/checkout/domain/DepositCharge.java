package com.ecopedia.charging.checkout.domain;

/**
 * Una seña cobrada, como la devuelve Pagos.
 *
 * @param chargeId el cobro en Pagos; es lo que se usa para devolverla.
 * @param cardLastFour la tarjeta con la que se cobró.
 */
public record DepositCharge(Long chargeId, String cardLastFour) {}
