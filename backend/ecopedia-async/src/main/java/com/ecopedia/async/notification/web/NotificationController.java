package com.ecopedia.async.notification.web;

import com.ecopedia.async.notification.domain.NotificationService;
import com.ecopedia.async.notification.web.dto.NotificationFeedResponse;
import com.ecopedia.async.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los avisos del conductor, por HTTP. Es la salida del componente; la entrada es la cola.
 *
 * <p><b>Siempre "mis avisos", nunca los de un id que llegue en la URL.</b> El destinatario sale del
 * token, así que no hay forma de pedir los avisos de otro: no existe la ruta. Marcar uno como
 * leído sí recibe un id —el del aviso—, y ahí es el servicio el que verifica que sea tuyo.
 *
 * <p>{@code isAuthenticated()} y no {@code hasRole('CONDUCTOR')}: hoy solo los conductores reciben
 * avisos, pero un operador que consulte los suyos no está pidiendo nada indebido —recibe una lista
 * vacía—. El mismo criterio que la disponibilidad en Reservas.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Los últimos avisos y cuántos hay sin leer.
     *
     * @param after el id del aviso más nuevo que la pantalla ya tiene. Con él, la respuesta trae solo
     *     lo que llegó después: es la consulta que se repite cada pocos segundos.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public NotificationFeedResponse myNotifications(
            Authentication authentication, @RequestParam(required = false) Long after) {
        return NotificationFeedResponse.fromDomain(
                notificationService.getHistory(AuthenticatedUser.idOf(authentication), after));
    }

    @PostMapping("/{notificationId}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsRead(Authentication authentication, @PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId, AuthenticatedUser.idOf(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
        notificationService.markAllAsRead(AuthenticatedUser.idOf(authentication));
        return ResponseEntity.noContent().build();
    }
}
