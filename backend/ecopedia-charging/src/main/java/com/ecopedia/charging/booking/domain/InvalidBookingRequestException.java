package com.ecopedia.charging.booking.domain;

/** El pedido está mal armado: una ventana al revés, o que ya empezó. Se responde 400. */
public class InvalidBookingRequestException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidBookingRequestException(String message) {
        super(message);
    }
}
