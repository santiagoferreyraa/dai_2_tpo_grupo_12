package com.ecopedia.integration.payment.internal;

import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Cuerpo opcional del pedido interno de reintegro de seña (RF13, ECO-59).
 *
 * <p>Cuando no viene cuerpo (o {@code amount} es {@code null}), se reintegra la seña completa.
 * Cuando se informa {@code amount}, se reintegra esa diferencia contra la seña por falla con la
 * carga en curso.
 */
public record DepositRefundBody(@Positive BigDecimal amount) {}
