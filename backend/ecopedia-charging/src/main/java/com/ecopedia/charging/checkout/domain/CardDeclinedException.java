package com.ecopedia.charging.checkout.domain;

/** La pasarela rechazó cobrar la seña con esa tarjeta: no salió plata y no se confirma. */
public class CardDeclinedException extends RuntimeException {

    public CardDeclinedException() {
        super("La pasarela rechazó el cobro de la seña");
    }
}
