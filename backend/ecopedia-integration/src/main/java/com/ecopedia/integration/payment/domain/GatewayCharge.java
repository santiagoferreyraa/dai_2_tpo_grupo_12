package com.ecopedia.integration.payment.domain;

/**
 * Lo que contestó la pasarela a un cobro.
 *
 * @param paymentId el id del cobro en la pasarela; es lo que se usa para reintegrarlo.
 * @param approved si salió la plata.
 * @param statusDetail el motivo que da la pasarela ({@code accredited},
 *     {@code cc_rejected_insufficient_amount}...). Es para decidir y para el log, no para la
 *     pantalla.
 */
public record GatewayCharge(String paymentId, boolean approved, String statusDetail) {}
