package com.ecopedia.async;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.async.notification.domain.Notification;
import com.ecopedia.async.notification.domain.NotificationRepository;
import com.ecopedia.async.notification.domain.NotificationToDispatch;
import com.ecopedia.async.notification.domain.NotificationType;
import com.ecopedia.async.notification.domain.StationInfo;
import com.ecopedia.async.stats.domain.DemandSlot;
import com.ecopedia.async.stats.domain.DemandSlotRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Las consultas propias de async contra PostgreSQL de verdad (QA-24): marcar todos los avisos como
 * leídos o borrados, que son actualizaciones masivas escritas a mano, y el rango de franjas del
 * tablero de Estadísticas, filtrado por el dueño de la estación.
 *
 * <p>Cada prueba usa un conductor o un operador propio, porque la base es la misma para todas las
 * clases *IT.
 */
class AsyncPostgresIT extends ContainersSupport {

    private static final Instant NOW = Instant.parse("2026-11-27T15:00:00Z");

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private DemandSlotRepository slots;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway aplica todas las migraciones de async en PostgreSQL, sin fallas")
    void migrationsRunCleanOnPostgres() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from async.flyway_schema_history where success = false", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from async.flyway_schema_history where success", Integer.class))
                .isPositive();
    }

    @Test
    @DisplayName("Marcar todo como leído toca solo los avisos sin leer de ese conductor")
    void markAllAsReadOnlyTouchesTheDriversUnread() {
        long driver = newId();
        long other = newId();
        notifications.save(notification(driver));
        notifications.save(notification(driver));
        Notification alreadyRead = notification(driver);
        alreadyRead.markReadAt(NOW.minusSeconds(60));
        notifications.save(alreadyRead);
        notifications.save(notification(other));

        assertThat(notifications.markAllAsRead(driver, NOW)).isEqualTo(2);
        assertThat(notifications.countByRecipientIdAndReadAtIsNullAndDeletedAtIsNull(driver))
                .isZero();
        assertThat(notifications.countByRecipientIdAndReadAtIsNullAndDeletedAtIsNull(other))
                .isOne();
        assertThat(notifications.findById(alreadyRead.getId()).orElseThrow().getReadAt())
                .as("el que ya estaba leído conserva su fecha")
                .isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    @DisplayName("Borrar todo es lógico: el aviso sigue en la base y deja de listarse")
    void markAllAsDeletedIsLogical() {
        long driver = newId();
        Notification kept = notifications.save(notification(driver));
        notifications.save(notification(driver));

        assertThat(notifications.markAllAsDeleted(driver, NOW)).isEqualTo(2);
        assertThat(notifications.markAllAsDeleted(driver, NOW))
                .as("la segunda vez no queda nada por borrar")
                .isZero();
        assertThat(notifications.findByRecipientIdAndDeletedAtIsNullOrderByCreatedAtDesc(driver))
                .isEmpty();
        assertThat(notifications.findById(kept.getId())).isPresent();
        assertThat(notifications.existsByEventId(kept.getEventId()))
                .as("la deduplicación sigue viéndolo: una reentrega no lo revive")
                .isTrue();
    }

    @Test
    @DisplayName("Las franjas del tablero salen por rango semiabierto y solo las del operador pedido")
    void demandSlotsByOwnerAndRange() {
        long owner = newId();
        long otherOwner = newId();
        Instant from = DemandSlot.startOf(NOW.plus(Duration.ofDays(400)));
        Instant to = from.plus(Duration.ofHours(1));
        DemandSlot first = slot(owner, from);
        DemandSlot last = slot(owner, to.minus(DemandSlot.SLOT));
        slot(owner, to);
        slot(otherOwner, from);

        assertThat(slots.findByOwnerInRange(owner, from, to))
                .extracting(DemandSlot::getId)
                .containsExactly(first.getId(), last.getId());
        assertThat(slots.findInRange(from, to)).hasSize(3);
    }

    private DemandSlot slot(long owner, Instant start) {
        DemandSlot slot = DemandSlot.open(newId(), start);
        slot.assignStation(newId(), "Estación de prueba", owner, "CCS2");
        return slots.save(slot);
    }

    private static Notification notification(long driver) {
        NotificationToDispatch request = new NotificationToDispatch(
                "event-" + UUID.randomUUID(),
                NotificationType.BOOKING_CONFIRMED,
                driver,
                1L,
                7L,
                NOW.plus(Duration.ofDays(1)),
                NOW.plus(Duration.ofDays(1)).plus(Duration.ofHours(1)),
                null,
                null,
                null,
                null);
        return new Notification(
                request,
                new StationInfo(3L, "Palermo Soho", "Honduras 4800", "CCS2"),
                "Reserva confirmada",
                "Tu reserva quedó confirmada.",
                NOW);
    }

    private static long newId() {
        return ThreadLocalRandom.current().nextLong(1_000_000, 1_000_000_000);
    }
}
