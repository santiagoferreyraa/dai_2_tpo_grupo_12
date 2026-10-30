package com.ecopedia.async.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Pruebas de ataque sobre el historial de avisos (QA-07, ECO-157): JWT hostil e inyección.
 *
 * <p>Sin mass assignment: este artefacto no recibe cuerpos JSON del cliente. Los avisos se crean
 * solo desde la cola, nunca por HTTP.
 *
 * <p><b>El rechazo hoy es 403, no 401</b>: ver DEF-02 y {@link #REJECTED_AS_ANONYMOUS}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApiAttackTest {

    /** Lo que recibe hoy un pedido sin sesión válida. Los criterios piden 401: DEF-02 / ECO-183. */
    private static final int REJECTED_AS_ANONYMOUS = 403;

    /** Ids de reserva propios de esta clase, lejos de los que usa {@code NotificationApiTest}. */
    private static final AtomicLong NEXT_BOOKING = new AtomicLong(90_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository repository;

    @Value("${ecopedia.jwt.secret}")
    private String secret;

    private Notification givenNotification(long recipientId) {
        long bookingId = NEXT_BOOKING.incrementAndGet();
        NotificationToDispatch request = new NotificationToDispatch(
                "booking-" + bookingId + "-BOOKING_CONFIRMED",
                NotificationType.BOOKING_CONFIRMED,
                recipientId,
                bookingId,
                12L,
                Instant.parse("2026-11-30T13:00:00Z"),
                Instant.parse("2026-11-30T14:00:00Z"));
        return repository.save(new Notification(request, null, "Reserva confirmada", "Texto", Instant.now()));
    }

    private String driver(long userId) {
        return HostileTokens.valid(secret, userId, "CONDUCTOR");
    }

    // --- JWT hostil -------------------------------------------------------------------------

    @Test
    @DisplayName("JWT · Control: con un token válido, el conductor ve su aviso")
    void validTokenWorks() throws Exception {
        givenNotification(701L);

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, driver(701L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Reserva confirmada"));
    }

    @Test
    @DisplayName("JWT · Un token vencido no ve avisos")
    void rejectsExpiredToken() throws Exception {
        givenNotification(702L);

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.expired(secret, 702L, "CONDUCTOR")))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    @Test
    @DisplayName("JWT · Un token con el sub de otro conductor, sin volver a firmar, no ve sus avisos")
    void rejectsTamperedSubject() throws Exception {
        givenNotification(703L);
        String forged = HostileTokens.tampered(driver(704L), "\"sub\":\"704\"", "\"sub\":\"703\"");

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, forged))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    @Test
    @DisplayName("JWT · Un token con alg=none, sin firma, no ve avisos")
    void rejectsAlgNone() throws Exception {
        givenNotification(705L);

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.algNone(705L, "CONDUCTOR")))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    @Test
    @DisplayName("JWT · Un token de otro emisor, firmado con su propia clave, no ve avisos")
    void rejectsTokenFromAnotherIssuer() throws Exception {
        givenNotification(706L);

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.fromAnotherIssuer(706L, "CONDUCTOR")))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    @Test
    @DisplayName("JWT · Un token vencido no puede borrar avisos, y el aviso sigue ahí")
    void expiredTokenCannotDelete() throws Exception {
        Notification notification = givenNotification(707L);

        mockMvc.perform(delete("/api/notifications/{id}", notification.getId())
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.expired(secret, 707L, "CONDUCTOR")))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, driver(707L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(notification.getId()));
    }

    // --- Inyección --------------------------------------------------------------------------

    @Test
    @DisplayName("Inyección · El parámetro 'after' con SQL responde 400, no la lista de todos")
    void afterRejectsSql() throws Exception {
        givenNotification(708L);
        givenNotification(709L);

        mockMvc.perform(get("/api/notifications")
                        .header(HttpHeaders.AUTHORIZATION, driver(708L))
                        .param("after", "0 OR 1=1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Inyección · Marcar como leído con SQL en el id responde 400 y no marca nada")
    void markAsReadRejectsSqlInPath() throws Exception {
        Notification notification = givenNotification(710L);

        mockMvc.perform(post("/api/notifications/{id}/read", "1 OR 1=1")
                        .header(HttpHeaders.AUTHORIZATION, driver(710L)))
                .andExpect(status().isBadRequest());

        assertThat(repository.findById(notification.getId())).isPresent();
        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, driver(710L)))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }
}
