package com.ecopedia.charging.booking.domain;

/** No hay ninguna reserva guardada con ese id. Se responde 404. */
public class BookingNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BookingNotFoundException(Long bookingId) {
        super("La reserva " + bookingId + " no existe");
    }
}
