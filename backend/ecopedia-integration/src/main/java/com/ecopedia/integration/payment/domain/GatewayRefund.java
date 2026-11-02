package com.ecopedia.integration.payment.domain;

import java.math.BigDecimal;

/**
 * Un reintegro hecho por la pasarela.
 *
 * @param refundId el id del reintegro en la pasarela.
 * @param paymentId el cobro al que pertenece.
 * @param amount lo que se devolvió.
 */
public record GatewayRefund(String refundId, String paymentId, BigDecimal amount) {}
