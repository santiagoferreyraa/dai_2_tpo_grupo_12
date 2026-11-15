package com.ecopedia.charging.session.service;

import com.ecopedia.charging.session.domain.TooManyAccessCodeAttemptsException;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tope de códigos de terminal errados por conductor (RF11, ECO-44).
 *
 * <p>El PIN de 6 dígitos acredita que el conductor está frente al conector. Son un millón de
 * combinaciones: sin tope, un script con un token de conductor las recorre en minutos y "acreditar
 * presencia" deja de significar algo. Con {@code access-code-max-failures} errores seguidos el
 * conductor queda en espera durante {@code access-code-lockout}; un acierto borra la cuenta.
 *
 * <p>Vive en memoria, como la retención de Reservas, y por lo mismo: es estado de corta vida que vence
 * solo, y el contenedor lo crea, lo limpia ({@link #purgeExpired}) y lo destruye. Si el proceso se
 * reinicia la cuenta vuelve a cero, y está bien: el tope es contra la fuerza bruta, que no sobrevive
 * a esperar un reinicio.
 */
@Component
public class AccessCodeAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(AccessCodeAttemptLimiter.class);

    private final int maxFailures;
    private final Duration lockout;
    private final Clock clock;
    private final Map<Long, Failures> failuresByDriver = new ConcurrentHashMap<>();

    public AccessCodeAttemptLimiter(
            @Value("${ecopedia.charging.access-code-max-failures:5}") int maxFailures,
            @Value("${ecopedia.charging.access-code-lockout:PT10M}") Duration lockout,
            Clock clock) {
        this.maxFailures = maxFailures;
        this.lockout = lockout;
        this.clock = clock;
    }

    /**
     * Antes de mirar el código: si el conductor está en espera, ni se lo pregunta a core.
     *
     * @throws TooManyAccessCodeAttemptsException si erró demasiadas veces y la espera no terminó
     */
    public void checkAllowed(Long driverId) {
        Failures failures = failuresByDriver.get(driverId);
        if (failures == null) {
            return;
        }
        Instant now = clock.instant();
        if (failures.isExpiredAt(now, lockout)) {
            failuresByDriver.remove(driverId, failures);
            return;
        }
        if (failures.count() >= maxFailures) {
            throw new TooManyAccessCodeAttemptsException(
                    driverId, Duration.between(now, failures.last().plus(lockout)));
        }
    }

    /** El código no abrió nada, o abrió otro conector. */
    public void recordFailure(Long driverId) {
        Instant now = clock.instant();
        Failures updated = failuresByDriver.merge(
                driverId,
                new Failures(1, now),
                (previous, first) ->
                        previous.isExpiredAt(now, lockout) ? first : new Failures(previous.count() + 1, now));
        if (updated.count() >= maxFailures) {
            log.warn(
                    "El conductor {} erró el código de la terminal {} veces seguidas: queda en espera",
                    driverId,
                    updated.count());
        }
    }

    /** El código fue correcto: la cuenta vuelve a cero. */
    public void recordSuccess(Long driverId) {
        failuresByDriver.remove(driverId);
    }

    /** Saca las cuentas vencidas para que el mapa no crezca con conductores que no volvieron a probar. */
    @Scheduled(fixedDelayString = "PT5M")
    public void purgeExpired() {
        Instant now = clock.instant();
        failuresByDriver.entrySet().removeIf(entry -> entry.getValue().isExpiredAt(now, lockout));
    }

    @PreDestroy
    void clear() {
        failuresByDriver.clear();
    }

    /** Cuántos errores seguidos, y cuándo fue el último: la espera se cuenta desde ahí. */
    private record Failures(int count, Instant last) {

        boolean isExpiredAt(Instant now, Duration lockout) {
            return !now.isBefore(last.plus(lockout));
        }
    }
}
