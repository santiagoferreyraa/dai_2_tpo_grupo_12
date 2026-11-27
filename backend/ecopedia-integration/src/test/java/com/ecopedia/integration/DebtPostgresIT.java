package com.ecopedia.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.integration.payment.domain.PendingPayment;
import com.ecopedia.integration.payment.domain.PendingPaymentRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Las deudas del conductor contra PostgreSQL de verdad (QA-24). El reclamo es lo que impide que el
 * barrido de reintentos y el botón "Pagar ahora" cobren la misma deuda dos veces: una sentencia
 * condicional en la que la base decide quién gana. Con H2 esa carrera no se prueba de verdad; acá
 * sí, con varios hilos a la vez.
 */
class DebtPostgresIT extends ContainersSupport {

    private static final Duration CLAIM = Duration.ofMinutes(2);

    @Autowired
    private PendingPaymentRepository debts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway aplica todas las migraciones de integration en PostgreSQL, sin fallas")
    void migrationsRunCleanOnPostgres() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from integration.flyway_schema_history where success = false", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from integration.flyway_schema_history where success", Integer.class))
                .isPositive();
    }

    @Test
    @DisplayName("Ocho reclamos a la vez sobre la misma deuda: gana uno solo, cada vez")
    void onlyOneConcurrentClaimWins() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < 10; round++) {
                Long id = newDebt(newDriverId()).getId();
                Instant now = Instant.now();
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Boolean>> claims = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    claims.add(pool.submit(() -> {
                        start.await();
                        return debts.claim(id, now, now.plus(CLAIM));
                    }));
                }
                start.countDown();

                int winners = 0;
                for (Future<Boolean> claim : claims) {
                    if (claim.get()) {
                        winners++;
                    }
                }
                assertThat(winners).as("ronda %d", round).isOne();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Un reclamo vigente bloquea al siguiente; vencido o soltado, se puede volver a reclamar")
    void claimExpiresAndCanBeReleased() {
        Long id = newDebt(newDriverId()).getId();
        Instant now = Instant.now();

        assertThat(debts.claim(id, now, now.plus(CLAIM))).isTrue();
        assertThat(debts.claim(id, now.plusSeconds(60), now.plusSeconds(60).plus(CLAIM)))
                .as("todavía reclamada")
                .isFalse();
        assertThat(debts.claim(id, now.plus(CLAIM).plusSeconds(1), now.plus(CLAIM.multipliedBy(2))))
                .as("el reclamo venció")
                .isTrue();

        debts.releaseClaim(id);
        assertThat(debts.claim(id, now.plus(CLAIM).plusSeconds(2), now.plus(CLAIM.multipliedBy(3))))
                .as("soltada")
                .isTrue();
    }

    @Test
    @DisplayName("Una deuda saldada no se puede reclamar")
    void settledDebtCannotBeClaimed() {
        PendingPayment debt = newDebt(newDriverId());
        debt.markSettled(Instant.now());
        debts.save(debt);

        assertThat(debts.claim(debt.getId(), Instant.now(), Instant.now().plus(CLAIM)))
                .isFalse();
    }

    @Test
    @DisplayName("Los reintentos vencidos salen; los que todavía no tocan, no")
    void dueRetriesAreFound() {
        Instant now = Instant.now();
        PendingPayment due = newDebt(newDriverId());
        due.scheduleFirstRetry(Duration.ofMinutes(-1));
        debts.save(due);
        PendingPayment later = newDebt(newDriverId());
        later.scheduleFirstRetry(Duration.ofMinutes(20));
        debts.save(later);

        List<Long> found =
                debts.findDueForRetry(now).stream().map(PendingPayment::getId).toList();

        assertThat(found).contains(due.getId()).doesNotContain(later.getId());
    }

    private PendingPayment newDebt(long driverId) {
        return debts.save(new PendingPayment(
                driverId,
                null,
                new BigDecimal("1500.00"),
                "Fondos insuficientes",
                "consumption-" + UUID.randomUUID(),
                Instant.now()));
    }

    private static long newDriverId() {
        return ThreadLocalRandom.current().nextLong(1_000_000, 1_000_000_000);
    }
}
