package com.ecopedia.integration.payment.domain;

/** Reservas no contestó al pedirle que anule las reservas de una cuenta marcada (ECO-62). */
public class BookingsUnavailableException extends RuntimeException {

    public BookingsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
