package com.ecopedia.async.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.async.notification.domain.Notification;
import com.ecopedia.async.notification.domain.NotificationRepository;
import com.ecopedia.async.notification.domain.NotificationToDispatch;
import com.ecopedia.async.notification.domain.NotificationType;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Los avisos por HTTP, con el artefacto entero: quién ve qué, la consulta de "lo nuevo" y el
 * marcado como leído.
 *
 * <p>Los avisos se cargan directo en el repositorio, sin pasar por la cola: lo que se prueba acá es
 * la salida del componente. La entrada la prueba {@code BookingNotificationFlowTest}.
 *
 * <p>Cada prueba usa conductores propios, porque el contexto de Spring —y con él la base— se
 * comparte con las otras clases de test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class NotificationApiTest {

    private static final AtomicLong NEXT_BOOKING = new AtomicLong(5_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository repository;

    @Value("${ecopedia.jwt.secret}")
    private String secret;

    private String driver(long userId) {
        return TestTokens.bearer(secret, userId, "CONDUCTOR");
    }

    private Notification saved(long recipientId) {
        long bookingId = NEXT_BOOKING.incrementAndGet();
        NotificationToDispatch request = new NotificationToDispatch(
                "booking-" + bookingId + "-BOOKING_CONFIRMED",
                NotificationType.BOOKING_CONFIRMED,
                recipientId,
                bookingId,
                12L,
                Instant.parse("2026-09-30T13:00:00Z"),
                Instant.parse("2026-09-30T14:00:00Z"));
        return repository.save(new Notification(request, null, "Reserva confirmada", "Texto", Instant.now()));
    }

    @Test
    @DisplayName("Sin sesión no se ven avisos")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cada conductor ve solo sus avisos, del más nuevo al más viejo, con cuántos tiene sin leer")
    void listsOnlyOwnNotifications() throws Exception {
        Notification older = saved(1001L);
        Notification newer = saved(1001L);
        saved(1002L);

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, driver(1001L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(newer.getId()))
                .andExpect(jsonPath("$.items[1].id").value(older.getId()))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.unreadCount").value(2));
    }

    /* La consulta que la pantalla repite cada pocos segundos: solo lo que llegó después. */
    @Test
    @DisplayName("Con 'after' trae solo los avisos posteriores al que la pantalla ya tiene")
    void afterReturnsOnlyNewerNotifications() throws Exception {
        Notification seen = saved(1003L);
        Notification fresh = saved(1003L);

        mockMvc.perform(get("/api/notifications")
                        .param("after", seen.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, driver(1003L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(fresh.getId()))
                .andExpect(jsonPath("$.unreadCount").value(2));
    }

    @Test
    @DisplayName("Marcar un aviso como leído baja el contador, y marcarlo dos veces no es un error")
    void markOneAsRead() throws Exception {
        Notification notification = saved(1004L);
        saved(1004L);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/notifications/{id}/read", notification.getId())
                            .header(HttpHeaders.AUTHORIZATION, driver(1004L)))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, driver(1004L)))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    @DisplayName("Nadie marca como leído el aviso de otro conductor")
    void cannotMarkSomeoneElses() throws Exception {
        Notification notification = saved(1005L);

        mockMvc.perform(post("/api/notifications/{id}/read", notification.getId())
                        .header(HttpHeaders.AUTHORIZATION, driver(1006L)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, driver(1005L)))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    @DisplayName("Un aviso que no existe no se marca")
    void unknownNotification() throws Exception {
        mockMvc.perform(post("/api/notifications/{id}/read", 999_999L).header(HttpHeaders.AUTHORIZATION, driver(1007L)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Marcar todos deja en cero al conductor y no toca los avisos de otro")
    void markAllAsRead() throws Exception {
        saved(1008L);
        saved(1008L);
        saved(1009L);

        mockMvc.perform(post("/api/notifications/read-all").header(HttpHeaders.AUTHORIZATION, driver(1008L)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, driver(1008L)))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.items[0].read").value(true));
        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, driver(1009L)))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }
}
