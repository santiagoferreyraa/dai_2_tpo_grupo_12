package com.ecopedia.async.notification.domain;

import java.util.List;

/**
 * Puerto de persistencia del historial de avisos.
 *
 * <p>Interfaz del dominio y no un {@code JpaRepository} a la vista del servicio, igual que en Pagos:
 * la capa de negocio pide lo que necesita, y la de datos decide cómo se resuelve (patrón DAO).
 */
public interface NotificationRepository {

    Notification save(Notification notification);

    boolean existsByEventId(String eventId);

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);
}
