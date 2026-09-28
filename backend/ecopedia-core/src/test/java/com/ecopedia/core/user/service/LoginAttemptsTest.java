package com.ecopedia.core.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El contador de logins fallidos, con un reloj que se mueve a mano: así el vencimiento del bloqueo
 * se prueba sin esperar quince minutos.
 */
class LoginAttemptsTest {

    /** Un reloj que avanza solo cuando el test lo pide. */
    private static final class ManualClock extends Clock {
        private Instant now = Instant.parse("2026-09-28T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final ManualClock clock = new ManualClock();
    private final LoginAttempts attempts = new LoginAttempts(clock);

    private void failTimes(String email, int times) {
        for (int i = 0; i < times; i++) {
            attempts.recordFailure(email);
        }
    }

    @Test
    @DisplayName("Con un fallo menos que el máximo la cuenta sigue abierta; con el máximo, se bloquea")
    void locksOnTheFifthFailure() {
        failTimes("a@ecopedia.test", LoginAttempts.MAX_FAILURES - 1);
        assertThat(attempts.lockRemaining("a@ecopedia.test")).isEmpty();

        attempts.recordFailure("a@ecopedia.test");
        assertThat(attempts.lockRemaining("a@ecopedia.test")).contains(LoginAttempts.LOCK);
    }

    @Test
    @DisplayName("El bloqueo vence solo, y el email se compara sin mayúsculas ni espacios")
    void theLockExpires() {
        failTimes("B@Ecopedia.test ", LoginAttempts.MAX_FAILURES);
        assertThat(attempts.lockRemaining("b@ecopedia.test")).isPresent();

        clock.advance(LoginAttempts.LOCK);
        assertThat(attempts.lockRemaining("b@ecopedia.test")).isEmpty();
    }

    @Test
    @DisplayName("Un login correcto borra los fallos anteriores")
    void successResetsTheCount() {
        failTimes("c@ecopedia.test", LoginAttempts.MAX_FAILURES - 1);
        attempts.recordSuccess("c@ecopedia.test");
        failTimes("c@ecopedia.test", LoginAttempts.MAX_FAILURES - 1);

        assertThat(attempts.lockRemaining("c@ecopedia.test")).isEmpty();
    }

    @Test
    @DisplayName("Los fallos viejos no suman: cinco errores separados por más del plazo no bloquean")
    void oldFailuresAreForgotten() {
        failTimes("d@ecopedia.test", LoginAttempts.MAX_FAILURES - 1);
        clock.advance(LoginAttempts.LOCK.plusSeconds(1));
        attempts.recordFailure("d@ecopedia.test");

        assertThat(attempts.lockRemaining("d@ecopedia.test")).isEmpty();
    }
}
