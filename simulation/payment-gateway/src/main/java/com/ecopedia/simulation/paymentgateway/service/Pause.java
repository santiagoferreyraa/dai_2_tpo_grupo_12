package com.ecopedia.simulation.paymentgateway.service;

import java.time.Duration;

/** La demora de la tarjeta lenta. Existe para que la espera se lea como lo que es: parte de la simulación. */
final class Pause {

    private Pause() {}

    static void of(Duration duration) {
        if (duration.isZero() || duration.isNegative()) return;
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
