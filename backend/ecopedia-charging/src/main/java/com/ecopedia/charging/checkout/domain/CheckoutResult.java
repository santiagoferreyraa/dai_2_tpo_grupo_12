package com.ecopedia.charging.checkout.domain;

import com.ecopedia.charging.booking.domain.Booking;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Resultado consolidado retornado por el Facade de Checkout tras orquestar los subsistemas (ECO-35).
 *
 * @param booking Reserva confirmada y persistida en base de datos
 * @param depositAmount Importe calculado para la seña/depósito (RF06, RF14)
 * @param paymentSummary Resumen descriptivo del estado del cobro o validación del medio de pago
 * @param checkedOutAt Instante en que finalizó la orquestación del checkout
 */
public record CheckoutResult(Booking booking, BigDecimal depositAmount, String paymentSummary, Instant checkedOutAt) {}
