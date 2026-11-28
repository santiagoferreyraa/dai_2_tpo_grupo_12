package com.ecopedia.charging.checkout.domain;

/** La pasarela rechazó cobrar la seña con esa tarjeta: no salió plata y no se confirma. */
public class CardDeclinedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CardDeclinedException() {
        super("La pasarela rechazó el cobro de la seña");
    }

    public CardDeclinedException(Throwable cause) {
        super("La pasarela rechazó el cobro de la seña", cause);
    }
}
