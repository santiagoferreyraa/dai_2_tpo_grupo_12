package com.ecopedia.integration.payment.domain;

import java.math.BigDecimal;

/**
 * Un pedido de cobro de seña, tal como lo hace el checkout (ECO-53).
 *
 * @param paymentMethodId la tarjeta que eligió el conductor, o {@code null} para la más reciente
 *     que se pueda usar.
 * @param amount lo que fijó Tarificación. Lo decide quien llama —el checkout—, nunca el conductor:
 *     por eso esta operación no tiene ruta pública.
 * @param reference a qué corresponde el cobro ("hold-&lt;uuid&gt;"), para el log y la pasarela.
 * @param idempotencyKey la misma en cada reintento del mismo cobro.
 */
public record DepositChargeRequest(
        Long driverId, Long paymentMethodId, BigDecimal amount, String reference, String idempotencyKey) {}
