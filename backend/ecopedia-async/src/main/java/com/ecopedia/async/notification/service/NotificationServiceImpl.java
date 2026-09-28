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
                ? repository.findTop50ByRecipientIdOrderByIdDesc(recipientId)
                : repository.findTop50ByRecipientIdAndIdGreaterThanOrderByIdDesc(recipientId, afterId);
        return new NotificationFeed(items, repository.countByRecipientIdAndReadAtIsNull(recipientId));
    }

    @Override
    public void markAsRead(Long notificationId, Long recipientId) {
        Notification notification = repository
                .findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        if (!notification.belongsTo(recipientId)) {
            throw new NotificationAccessDeniedException(notificationId);
        }
        // Idempotente, como cancelar una reserva: marcar dos veces no es un error.
        notification.markReadAt(clock.instant());
        repository.save(notification);
    }

    @Override
    public void markAllAsRead(Long recipientId) {
        repository.markAllAsRead(recipientId, clock.instant());
    }
}
