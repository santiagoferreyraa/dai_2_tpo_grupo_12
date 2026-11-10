package com.ecopedia.charging.booking.domain;

import java.time.Instant;

/**
 * {@code CONFIRMED}: el conductor reservó y pagó la seña, y todavía no se presentó. Es el único
 * estado que admite cancelar, marcar la incomparecencia y habilitar la carga; además, lo que
 * comparte con {@code IN_PROGRESS} ({@link ActiveBookingState}).
 */
final class ConfirmedBookingState extends ActiveBookingState {

    /**
     * Libera la ventana y decide la seña: se devuelve si se cancela hasta el plazo de preaviso
     * inclusive —5 horas antes del inicio, decidido por Manuel el 31/10— y queda para el operador si
     * se cancela después. Una reserva cuya ventana ya terminó no se cancela: no tiene nada que liberar,
     * y dejar reescribirla sería borrar su historial.
     */
    @Override
    public void cancel(Booking booking, Instant now) {
        if (!booking.getWindow().end().isAfter(now)) {
            throw new BookingStateConflictException(booking.getId(), "terminada", "la cancelación");
        }
        booking.moveTo(BookingStatus.CANCELLED);
        booking.settleDepositOnCancellation(now);
    }

    /** La seña queda para el operador: es para lo que existe. */
    @Override
    public void markNoShow(Booking booking) {
        booking.moveTo(BookingStatus.NO_SHOW);
        booking.forfeitDeposit();
    }

    /** La reserva se consume al habilitar la carga. */
    @Override
    public void startCharging(Booking booking, Instant now) {
        booking.moveTo(BookingStatus.IN_PROGRESS);
    }
}
