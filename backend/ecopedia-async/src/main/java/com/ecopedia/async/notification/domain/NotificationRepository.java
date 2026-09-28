package com.ecopedia.async.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia del historial de avisos.
 *
 * <p>Interfaz del dominio y no un {@code JpaRepository} a la vista del servicio, igual que en Pagos:
 * la capa de negocio pide lo que necesita, y la de datos decide cómo se resuelve (patrón DAO).
 */
public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(Long id);

    boolean existsByEventId(String eventId);

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    /** Los últimos avisos del conductor, del más nuevo al más viejo. */
    List<Notification> findTop50ByRecipientIdOrderByIdDesc(Long recipientId);

    /** Los avisos del conductor posteriores a uno dado: lo nuevo desde la última consulta. */
    List<Notification> findTop50ByRecipientIdAndIdGreaterThanOrderByIdDesc(Long recipientId, Long afterId);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    /** Marca como leídos todos los avisos sin leer del conductor. Devuelve cuántos marcó. */
    int markAllAsRead(Long recipientId, Instant when);
}
