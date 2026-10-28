package com.ecopedia.simulation.paymentgateway.web.dto;

import java.math.BigDecimal;

/** {@code POST /v1/payments/{id}/refunds}. Sin {@code amount}, se devuelve todo lo que queda. */
public record CreateRefundRequest(BigDecimal amount) {}
