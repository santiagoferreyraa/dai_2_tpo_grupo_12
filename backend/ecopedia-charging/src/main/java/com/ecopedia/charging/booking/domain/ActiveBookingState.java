package com.ecopedia.charging.booking.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lo que comparten {@code CONFIRMED} e {@code IN_PROGRESS}: una reserva que todavía ocupa el conector
 * puede liberarse o liquidarse por una falla ajena al conductor (RF13), y puede cerrarse cuando
 * termina la carga. Las tres transiciones valen igual en los dos estados.
 */
abstract class ActiveBookingState implements BookingState {

    /** Se libera sin penalización y la seña se reintegra entera, sin importar el plazo de preaviso. */
    @Override
    public void failActivation(Booking booking) {
        booking.moveTo(BookingStatus.CANCELLED);
        booking.refundWholeDeposit();
    }

    /**
     * Se liquida el consumo real sin el piso de la seña: si fue menor, se reintegra la diferencia. Sin
     * consumo, es lo mismo que no haber podido empezar. El estado no cambia.
     */
    @Override
    public void settleFaultedCharge(Booking booking, BigDecimal realCost, Instant now) {
        if (realCost == null || realCost.signum() < 0) {
            throw new InvalidBookingRequestException("El consumo real no puede ser negativo");
        }
        if (realCost.signum() == 0) {
            failActivation(booking);
            return;
        }
        booking.trimWindowAt(now);
        booking.settleDepositAgainst(realCost);
    }

    /** Se cierra y, si la carga terminó antes de lo reservado, se libera el resto de la ventana. */
    @Override
    public void completeCharging(Booking booking, Instant stoppedAt) {
        if (stoppedAt != null) {
            booking.trimWindowAt(stoppedAt);
        }
        booking.moveTo(BookingStatus.COMPLETED);
    }
}
