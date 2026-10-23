package com.ecopedia.core.user.domain;

import java.time.Duration;

/**
 * El login se rechazó sin mirar la contraseña, porque la cuenta está bloqueada por demasiados
 * intentos fallidos. Trae cuánto falta para poder volver a probar.
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super("Demasiados intentos fallidos. Probá de nuevo en " + minutesOf(retryAfter) + ".");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    private static String minutesOf(Duration duration) {
        long minutes = Math.max(1, (duration.toSeconds() + 59) / 60);
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }
}
