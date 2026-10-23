package com.ecopedia.core.user.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cuenta los logins fallidos por email y bloquea la cuenta un rato cuando son demasiados.
 *
 * <p><b>Por qué existe.</b> Sin límite, se podían probar contraseñas contra una cuenta sin freno:
 * fuerza bruta a la velocidad que diera la red. Con {@value #DEFAULT_MAX_FAILURES} fallos seguidos
 * la cuenta queda bloqueada quince minutos, y mientras dure ni la contraseña correcta entra.
 *
 * <p><b>Los dos números salen de la configuración</b> ({@code ECOPEDIA_LOGIN_MAX_FAILURES} y
 * {@code ECOPEDIA_LOGIN_LOCK_MINUTES} en el .env), con 5 y 15 por defecto. Sirve para la demo: con
 * el bloqueo en un minuto se muestra entero sin quedarse afuera un cuarto de hora.
 *
 * <p><b>Se cuenta por email, exista o no la cuenta.</b> Si solo se bloquearan las que existen, el
 * bloqueo le diría a quien prueba qué emails están registrados.
 *
 * <p><b>Vive en memoria, y alcanza.</b> Un reinicio borra los contadores, pero el atacante no
 * controla los reinicios. Una tabla haría falta con varias instancias de core detrás de un
 * balanceador, que no es el caso.
 */
@Component
public class LoginAttempts {

    static final int DEFAULT_MAX_FAILURES = 5;

    static final Duration DEFAULT_LOCK = Duration.ofMinutes(15);

    private final Map<String, Entry> byEmail = new ConcurrentHashMap<>();
    private final int maxFailures;

    /** Cuánto dura el bloqueo, y también cuánto se recuerda un fallo suelto. */
    private final Duration lock;

    private final Clock clock;

    @Autowired
    public LoginAttempts(
            @Value("${ecopedia.login.max-failures:5}") int maxFailures,
            @Value("${ecopedia.login.lock-minutes:15}") long lockMinutes) {
        this(maxFailures, Duration.ofMinutes(lockMinutes), Clock.systemUTC());
    }

    public LoginAttempts() {
        this(DEFAULT_MAX_FAILURES, DEFAULT_LOCK, Clock.systemUTC());
    }

    LoginAttempts(Clock clock) {
        this(DEFAULT_MAX_FAILURES, DEFAULT_LOCK, clock);
    }

    LoginAttempts(int maxFailures, Duration lock, Clock clock) {
        // Un cero o un negativo en el .env apagaría el bloqueo sin que nadie se entere: mejor que
        // core no arranque y el error diga cuál de los dos valores está mal.
        if (maxFailures < 1) {
            throw new IllegalArgumentException("ecopedia.login.max-failures tiene que ser al menos 1");
        }
        if (lock.isNegative() || lock.isZero()) {
            throw new IllegalArgumentException("ecopedia.login.lock-minutes tiene que ser al menos 1");
        }
        this.maxFailures = maxFailures;
        this.lock = lock;
        this.clock = clock;
    }

    /** Cuánto le falta al bloqueo de ese email, o vacío si no está bloqueado. */
    public Optional<Duration> lockRemaining(String email) {
        Entry entry = byEmail.get(key(email));
        Instant now = clock.instant();
        if (entry == null || entry.lockedUntil() == null || !now.isBefore(entry.lockedUntil())) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, entry.lockedUntil()));
    }

    /** Anota un fallo. Al llegar al máximo, bloquea y vuelve a contar desde cero. */
    public void recordFailure(String email) {
        Instant now = clock.instant();
        byEmail.compute(key(email), (ignored, entry) -> {
            // Un fallo viejo no suma: sin esto, cinco errores de tipeo en un mes bloquearían.
            boolean fresh = entry == null || now.isAfter(entry.firstFailure().plus(lock));
            int failures = fresh ? 1 : entry.failures() + 1;
            Instant first = fresh ? now : entry.firstFailure();
            return failures >= maxFailures ? new Entry(0, now, now.plus(lock)) : new Entry(failures, first, null);
        });
    }

    /** Un login correcto borra los fallos anteriores. */
    public void recordSuccess(String email) {
        byEmail.remove(key(email));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record Entry(int failures, Instant firstFailure, Instant lockedUntil) {}
}
