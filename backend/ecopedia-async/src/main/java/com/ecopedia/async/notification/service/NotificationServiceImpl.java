package com.ecopedia.async.notification.service;

import com.ecopedia.async.notification.domain.EmailSender;
import com.ecopedia.async.notification.domain.Notification;
import com.ecopedia.async.notification.domain.NotificationAccessDeniedException;
import com.ecopedia.async.notification.domain.NotificationFeed;
import com.ecopedia.async.notification.domain.NotificationNotFoundException;
import com.ecopedia.async.notification.domain.NotificationRepository;
import com.ecopedia.async.notification.domain.NotificationService;
import com.ecopedia.async.notification.domain.NotificationToDispatch;
import com.ecopedia.async.notification.domain.StationDirectory;
import com.ecopedia.async.notification.domain.StationInfo;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Implementación de {@link NotificationService}.
 *
 * <p><b>El orden importa: descartar el duplicado, guardar, y recién después mandar el mail.</b>
 * Si el mail saliera antes de guardar y el guardado fallara, el mensaje volvería a la cola y el
 * conductor recibiría el mismo mail dos veces. Guardando primero, una reentrega encuentra el aviso
 * ya guardado y se descarta sin volver a mandar nada.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository repository;
    private final StationDirectory stationDirectory;
    private final EmailSender emailSender;
    private final Clock clock;
    private final NotificationTemplates templates;

    public NotificationServiceImpl(
            NotificationRepository repository,
            StationDirectory stationDirectory,
            EmailSender emailSender,
            Clock clock,
            @Value("${ecopedia.notifications.zone}") ZoneId zone) {
        this.repository = repository;
        this.stationDirectory = stationDirectory;
        this.emailSender = emailSender;
        this.clock = clock;
        this.templates = new NotificationTemplates(zone);
    }

    @Override
    public void dispatch(NotificationToDispatch request) {
        if (repository.existsByEventId(request.eventId())) {
            log.info("Aviso {} repetido: ya se había procesado, se descarta", request.eventId());
            return;
        }

        StationInfo station =
                stationDirectory.findByConnector(request.connectorId()).orElse(null);
        String title = templates.title(request);
        String body = templates.body(request, station);

        try {
            repository.save(new Notification(request, station, title, body, clock.instant()));
        } catch (DataIntegrityViolationException duplicate) {
            /*
             * Dos entregas del mismo mensaje al mismo tiempo: las dos pasaron la consulta de arriba y
             * la restricción UNIQUE dejó guardar a una sola. La otra es un duplicado, no un error, y
             * no tiene que volver a la cola.
             */
            log.info("Aviso {} repetido: otra entrega lo guardó primero, se descarta", request.eventId());
            return;
        }

        log.info("Aviso {} guardado para el conductor {}", request.eventId(), request.recipientId());
        emailSender.send(request.recipientId(), title, body);
    }

    @Override
    public NotificationFeed getHistory(Long recipientId, Long afterId) {
        List<Notification> items = afterId == null
                ? repository.findTop50ByRecipientIdAndDeletedAtIsNullOrderByIdDesc(recipientId)
                : repository.findTop50ByRecipientIdAndIdGreaterThanAndDeletedAtIsNullOrderByIdDesc(
                        recipientId, afterId);
        return new NotificationFeed(items, repository.countByRecipientIdAndReadAtIsNullAndDeletedAtIsNull(recipientId));
    }

    @Override
    public void markAsRead(Long notificationId, Long recipientId) {
        Notification notification = mine(notificationId, recipientId);
        /*
         * Un aviso borrado no se marca: para la pantalla ya no existe, y el 404 es la respuesta
         * honesta a "marcá como leído esto que no está en tu buzón". El caso llega solo por una
         * carrera —la pantalla lo tenía a la vista cuando otra pestaña lo borró— y lo resuelve la
         * próxima consulta, que ya no lo trae.
         */
        if (notification.isDeleted()) {
            throw new NotificationNotFoundException(notificationId);
        }
        // Idempotente, como cancelar una reserva: marcar dos veces no es un error.
        notification.markReadAt(clock.instant());
        repository.save(notification);
    }

    @Override
    public void markAllAsRead(Long recipientId) {
        repository.markAllAsRead(recipientId, clock.instant());
    }

    /*
     * Borrar es marcar, no un DELETE. El motivo está en la migración add_notifications_deleted_at y
     * es de este componente: la fila es la prueba de que el mensaje de la cola ya se procesó, y sin
     * ella una reentrega reviviría el aviso borrado y le repetiría el mail al conductor.
     *
     * No pasa por isDeleted(): borrar lo ya borrado deja todo como está —markDeletedAt conserva la
     * primera fecha— y contestar 204 es lo que hace que el reintento de una pantalla que perdió la
     * respuesta no se vea como un error.
     */
    @Override
    public void delete(Long notificationId, Long recipientId) {
        Notification notification = mine(notificationId, recipientId);
        notification.markDeletedAt(clock.instant());
        repository.save(notification);
    }

    @Override
    public void deleteAll(Long recipientId) {
        repository.markAllAsDeleted(recipientId, clock.instant());
    }

    /**
     * El aviso pedido, si existe y es de quien lo pide.
     *
     * <p>Está separado porque es la misma puerta para marcar y para borrar, y las dos tienen que
     * contestar lo mismo ante lo mismo: 404 si no existe, 403 si es de otro. Escrito dos veces, un
     * día una de las dos deja de mirar de quién es.
     */
    private Notification mine(Long notificationId, Long recipientId) {
        Notification notification = repository
                .findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        if (!notification.belongsTo(recipientId)) {
            throw new NotificationAccessDeniedException(notificationId);
        }
        return notification;
    }
}
