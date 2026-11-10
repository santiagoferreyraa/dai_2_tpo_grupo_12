package com.ecopedia.integration.payment.domain;

import java.math.BigDecimal;

/**
 * El cobro del consumo de una sesión de carga, al cerrarla (RF14, ECO-44).
 *
 * @param amount lo que queda por cobrar: el consumo menos la seña ya cobrada. Lo calcula
 *     SesionesDeCarga con Tarificación; Pagos no lo discute.
 * @param reference la sesión. Es también la base de la clave de idempotencia ante la pasarela, así
 *     que repetir el pedido no cobra dos veces.
 */
public record ConsumptionChargeRequest(Long driverId, BigDecimal amount, String reference) {}
