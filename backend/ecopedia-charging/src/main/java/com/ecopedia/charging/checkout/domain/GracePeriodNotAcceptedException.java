package com.ecopedia.charging.checkout.domain;

/**
 * Excepción lanzada cuando el conductor no ha aceptado la tolerancia de gracia de 15 minutos (RF09).
 */
public class GracePeriodNotAcceptedException extends RuntimeException {

    public GracePeriodNotAcceptedException() {
        super("Debe aceptar la tolerancia de 15 minutos (RF09) para confirmar la reserva");
    }
}
