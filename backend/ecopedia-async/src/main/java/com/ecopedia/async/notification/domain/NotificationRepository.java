package com.ecopedia.async.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia del historial de avisos.
 *
 * <p>Interfaz del dominio y no un {@code JpaRepository} a la vista del servicio, igual que en Pagos:
 * la capa de negocio pide lo que necesita, y la de datos decide cómo se resuelve (patrón DAO).
 *
 * <p><b>Todas las consultas del buzón llevan {@code AndDeletedAtIsNull} en el nombre</b>, y el
 * nombre largo es a propósito: un aviso borrado sigue estando en la tabla —ver la migración {@code
 * add_notifications_deleted_at}— así que "los avisos del conductor" y "los avisos que el conductor
 * ve" son dos consultas distintas, y la que se olvide el filtro devuelve lo que alguien borró. La
 * excepción es {@link #findById(Long)}, que es la búsqueda para escribir y tiene que encontrar
 * también los borrados: es lo que hace que borrar dos veces sea el mismo pedido y no un 404.
 */
public interface NotificationRepository {

    Notification save(Notification notification);

    /** Busca por id, borrados incluidos. Ver la nota de la interfaz sobre por qué los incluye. */
    Optional<Notification> findById(Long id);

    boolean existsByEventId(String eventId);

    List<Notification> findByRecipientIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long recipientId);

    /** Los últimos avisos del conductor, del más nuevo al más viejo. */
    List<Notification> findTop50ByRecipientIdAndDeletedAtIsNullOrderByIdDesc(Long recipientId);

    /** Los avisos del conductor posteriores a uno dado: lo nuevo desde la última consulta. */
    List<Notification> findTop50ByRecipientIdAndIdGreaterThanAndDeletedAtIsNullOrderByIdDesc(
            Long recipientId, Long afterId);

    long countByRecipientIdAndReadAtIsNullAndDeletedAtIsNull(Long recipientId);

    /** Marca como leídos todos los avisos sin leer del conductor. Devuelve cuántos marcó. */
    int markAllAsRead(Long recipientId, Instant when);

    /** Saca del buzón todos los avisos del conductor de una vez. Devuelve cuántos borró. */
    int markAllAsDeleted(Long recipientId, Instant when);
}
