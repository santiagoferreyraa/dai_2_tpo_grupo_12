package com.ecopedia.async.notification.web;

import com.ecopedia.async.notification.domain.NotificationService;
import com.ecopedia.async.notification.web.dto.NotificationFeedResponse;
import com.ecopedia.async.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * leído o borrarlo sí recibe un id —el del aviso—, y ahí es el servicio el que verifica que sea
 * tuyo.
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

    /**
     * Saca un aviso del buzón.
     *
     * <p>{@code DELETE} y no {@code POST .../delete} porque es lo que es: se pide que un recurso deje
     * de estar. Que por debajo sea una marca y no un {@code DELETE} de SQL —ver {@code
     * NotificationServiceImpl}— es asunto de la implementación y no del contrato: para quien llama,
     * el aviso se fue.
     *
     * <p>Repetirlo devuelve 204 igual, como manda el método: borrar dos veces deja el buzón en el
     * mismo estado. Los 404 y 403 son para el aviso que no existe y el que es de otro.
     */
    @DeleteMapping("/{notificationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable Long notificationId) {
        notificationService.delete(notificationId, AuthenticatedUser.idOf(authentication));
        return ResponseEntity.noContent().build();
    }

    /**
     * Vacía el buzón.
     *
     * <p>Va sobre la colección y no en {@code /delete-all}, que sería el espejo de {@code /read-all}:
     * marcar todas como leídas no tiene método HTTP propio y necesita inventar una ruta, borrar la
     * colección sí lo tiene. Con sesión ajena no hay nada que proteger acá porque no hay id que
     * pasar: se borra el buzón de quien viene en el token y de nadie más.
     */
    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteAll(Authentication authentication) {
        notificationService.deleteAll(AuthenticatedUser.idOf(authentication));
        return ResponseEntity.noContent().build();
    }
}
