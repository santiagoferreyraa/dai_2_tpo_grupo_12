package com.ecopedia.charging.booking.domain;

/**
 * La reserva no admite ese cambio en el estado en que está (QA-26): cancelar una que ya terminó o
 * en la que el conductor no se presentó, o liberar, liquidar o marcar como incomparecencia una que
 * ya no está confirmada.
 *
 * <p>Se responde <b>409</b> y no 400: el pedido está bien armado, lo que choca es el estado del
 * recurso. Con un {@code code} estable ({@link #CODE}) y un texto fijo; el mensaje de la excepción,
 * con el id y el estado, es para el log (directiva 7: sin pistas sobre el estado real).
 *
 * <p>La tira la propia {@link Booking}, no el servicio: así la regla vale para cualquiera que la
 * llame, también lo que se sume con SesionesDeCarga.
 */
public class BookingStateConflictException extends RuntimeException {

    public static final String CODE = "BOOKING_STATE_CONFLICT";

    public BookingStateConflictException(Long bookingId, String state, String transition) {
        super("La reserva " + bookingId + " está " + state + " y no admite " + transition);
    }
}
