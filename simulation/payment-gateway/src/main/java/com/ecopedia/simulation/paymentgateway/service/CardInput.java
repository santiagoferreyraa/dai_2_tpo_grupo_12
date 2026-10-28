package com.ecopedia.simulation.paymentgateway.service;

/**
 * Los datos de una tarjeta tal como llegan a tokenizar. Es lo único que ve el número, y no se guarda.
 *
 * @param number el número, con o sin espacios y guiones.
 * @param expiryMonth mes de vencimiento, 1 a 12.
 * @param expiryYear año de vencimiento de cuatro dígitos.
 * @param holderName nombre impreso. Se pide como lo pide cualquier pasarela; tampoco se guarda.
 */
public record CardInput(String number, int expiryMonth, int expiryYear, String holderName) {

    /** Tapa el número en cualquier log o traza: el {@code toString()} de un record los muestra todos. */
    @Override
    public String toString() {
        return "CardInput[number=****, expiry=" + expiryMonth + "/" + expiryYear + "]";
    }
}
