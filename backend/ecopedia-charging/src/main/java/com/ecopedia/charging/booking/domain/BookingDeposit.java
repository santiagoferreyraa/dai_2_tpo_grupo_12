package com.ecopedia.charging.booking.domain;

import java.math.BigDecimal;

/**
 * La seña con la que se confirma una reserva, ya cobrada.
 *
 * <p>Reservas no cobra: la recibe hecha del checkout y la anota. Meter Pagos acá sería el
 * acoplamiento que el Facade existe para evitar.
 *
 * @param amount lo que se cobró; 0 si no hubo seña.
 * @param chargeId el cobro en Pagos, para devolverlo; {@code null} si no hubo seña.
 * @param cardLastFour la tarjeta con la que se cobró, para nombrarla en los avisos.
 */
public record BookingDeposit(BigDecimal amount, Long chargeId, String cardLastFour) {

    public static BookingDeposit none() {
        return new BookingDeposit(BigDecimal.ZERO, null, null);
    }

    public boolean isCharged() {
        return chargeId != null && amount.signum() > 0;
    }
}
