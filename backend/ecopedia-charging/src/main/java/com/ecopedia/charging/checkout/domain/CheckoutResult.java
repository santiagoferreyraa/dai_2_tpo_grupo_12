package com.ecopedia.charging.checkout.domain;

import com.ecopedia.charging.booking.domain.Booking;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Resultado consolidado retornado por el Facade de Checkout tras orquestar los subsistemas (ECO-35).
 *
 * <p><b>Sale entero de la reserva guardada</b> (ECO-179): así un checkout repetido con la misma
 * {@code Idempotency-Key} devuelve exactamente la misma respuesta que el primero, aunque se arme
 * minutos después y la retención ya no exista.
 *
 * @param booking Reserva confirmada y persistida en base de datos
 * @param depositAmount Importe cobrado como seña (RF06, RF14); 0 si el esquema no la pide
 * @param paymentSummary Resumen del cobro, para el log y quien lea la respuesta a mano
 * @param checkedOutAt Instante en que se confirmó la reserva
 */
public record CheckoutResult(Booking booking, BigDecimal depositAmount, String paymentSummary, Instant checkedOutAt) {

    public static CheckoutResult of(Booking booking) {
        String summary = booking.getDepositAmount().signum() > 0
                ? "Seña de $" + booking.getDepositAmount() + " cobrada a la tarjeta terminada en "
                        + booking.getDepositCardLastFour()
                : "Sin seña";
        return new CheckoutResult(booking, booking.getDepositAmount(), summary, booking.getCreatedAt());
    }
}
