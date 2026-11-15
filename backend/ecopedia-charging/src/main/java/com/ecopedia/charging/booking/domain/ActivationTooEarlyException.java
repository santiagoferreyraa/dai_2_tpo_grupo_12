package com.ecopedia.charging.booking.domain;

import java.time.Instant;

/**
 * El conductor quiso habilitar la carga antes de tiempo: una reserva se puede habilitar recién desde
 * un rato antes de su inicio ({@code ecopedia.booking.early-activation}, 15 minutos, decidido por
 * Manuel el 12/11), y no horas antes, ocupando el conector fuera de su turno.
 *
 * <p>Se responde <b>409</b> con {@link #CODE} y {@link #getAvailableFrom()}, para que el front le
 * diga desde qué hora puede cargar. Es un dato de la propia reserva del conductor, no una pista
 * sobre el estado de otro.
 */
public class ActivationTooEarlyException extends RuntimeException {

    public static final String CODE = "ACTIVATION_TOO_EARLY";

    private final transient Instant availableFrom;

    public ActivationTooEarlyException(Long bookingId, Instant availableFrom) {
        super("La reserva " + bookingId + " recién se puede habilitar desde " + availableFrom);
        this.availableFrom = availableFrom;
    }

    public Instant getAvailableFrom() {
        return availableFrom;
    }
}
