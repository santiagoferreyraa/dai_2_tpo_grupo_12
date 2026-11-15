package com.ecopedia.charging.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecopedia.charging.session.domain.TerminalAccessCode;
import com.ecopedia.charging.session.domain.TooManyAccessCodeAttemptsException;
import com.ecopedia.charging.session.service.AccessCodeAttemptLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El código de la terminal y el tope de intentos (RF11, ECO-44). */
class TerminalAccessCodeTest {

    @Test
    @DisplayName("Se lee el PIN o el QR de la terminal, y nada más")
    void parse() {
        assertThat(TerminalAccessCode.parse("012345")).contains("012345");
        assertThat(TerminalAccessCode.parse(" ecopedia:connector:012345 ")).contains("012345");
        assertThat(TerminalAccessCode.parse("12345")).isEmpty();
        assertThat(TerminalAccessCode.parse("https://ejemplo.com/012345")).isEmpty();
        assertThat(TerminalAccessCode.parse(null)).isEmpty();
    }

    /** Un reloj que se puede adelantar a mano. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-02T15:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    @Test
    @DisplayName("Al llegar al tope queda en espera, y la espera termina sola")
    void lockoutExpires() {
        MovableClock clock = new MovableClock();
        AccessCodeAttemptLimiter limiter = new AccessCodeAttemptLimiter(3, Duration.ofMinutes(10), clock);

        limiter.recordFailure(1L);
        limiter.recordFailure(1L);
        limiter.checkAllowed(1L);
        limiter.recordFailure(1L);

        assertThatThrownBy(() -> limiter.checkAllowed(1L)).isInstanceOf(TooManyAccessCodeAttemptsException.class);
        // Otro conductor no paga por los errores de este.
        limiter.checkAllowed(2L);

        clock.advance(Duration.ofMinutes(10));
        limiter.checkAllowed(1L);
    }

    @Test
    @DisplayName("Un acierto borra la cuenta")
    void successResets() {
        AccessCodeAttemptLimiter limiter = new AccessCodeAttemptLimiter(
                2, Duration.ofMinutes(10), Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC));

        limiter.recordFailure(1L);
        limiter.recordSuccess(1L);
        limiter.recordFailure(1L);

        limiter.checkAllowed(1L);
    }
}
