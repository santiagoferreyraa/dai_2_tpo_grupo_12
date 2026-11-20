package com.ecopedia.integration.payment.web.dto;

/**
 * Con qué tarjeta paga el conductor su deuda (ECO-62).
 *
 * @param paymentMethodId la tarjeta elegida; nula para la más reciente que sirva
 */
public record PayDebtRequest(Long paymentMethodId) {}
