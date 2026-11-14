package com.ecopedia.charging.session.domain;

import java.time.Duration;

/**
 * El conductor erró el código demasiadas veces seguidas (ECO-44). Ver {@code AccessCodeAttemptLimiter}.
 *
 * @param retryAfter cuánto falta para poder volver a probar
 */
public class TooManyAccessCodeAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAccessCodeAttemptsException(Long driverId, Duration retryAfter) {
        super("El conductor " + driverId + " erró el código de la terminal demasiadas veces");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
