package com.ecopedia.simulation.paymentgateway.service;

import java.math.BigDecimal;

/**
 * Un pedido de cobro.
 *
 * @param cardTokenId el token de la tarjeta, nunca el número.
 * @param amount el importe, en pesos, mayor que cero.
 * @param description texto libre para el resumen ("Seña de la reserva 12").
 * @param externalReference la referencia de quien cobra, para encontrar el cobro desde su lado.
 */
public record ChargeRequest(String cardTokenId, BigDecimal amount, String description, String externalReference) {}
