package com.ecopedia.async.notification.data;

import com.ecopedia.async.notification.domain.Notification;
import com.ecopedia.async.notification.domain.NotificationRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link NotificationRepository} resuelto por Spring Data JPA sobre la tabla {@code notifications}. */
@Repository
public interface JpaNotificationRepository extends JpaRepository<Notification, Long>, NotificationRepository {

    @Override
    boolean existsByEventId(String eventId);

    @Override
    List<Notification> findByRecipientIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long recipientId);

    /*
     * Por id y no por fecha de creación: el id crece con cada aviso guardado, así que "posterior a
     * este id" es exacto. Dos avisos creados en el mismo milisegundo empatarían en la fecha, y una
     * consulta de "lo nuevo desde tal fecha" perdería o repetiría uno.
     */
    @Override
    List<Notification> findTop50ByRecipientIdAndDeletedAtIsNullOrderByIdDesc(Long recipientId);

    @Override
    List<Notification> findTop50ByRecipientIdAndIdGreaterThanAndDeletedAtIsNullOrderByIdDesc(
            Long recipientId, Long afterId);

    @Override
    long countByRecipientIdAndReadAtIsNullAndDeletedAtIsNull(Long recipientId);

    /*
     * Un UPDATE en la base y no "traer todos, marcarlos y guardarlos": con cien avisos sin leer
     * serían cien UPDATE sueltos. Solo toca los que no tienen lectura, así que la primera lectura
     * de cada aviso se conserva.
     *
     * Los borrados quedan afuera: marcar como leído algo que ya no está en el buzón no cambia nada
     * que se vea —el contador tampoco los cuenta— y escribirles la fecha de lectura ensuciaría el
     * registro de lo que el conductor leyó de verdad.
     */
    @Override
    @Modifying
    @Transactional
    @Query("update Notification n set n.readAt = :when"
            + " where n.recipientId = :recipientId and n.readAt is null and n.deletedAt is null")
    int markAllAsRead(@Param("recipientId") Long recipientId, @Param("when") Instant when);

    /*
     * El "borrar todas" de la pantalla, por lo mismo que el de arriba: un UPDATE y no un viaje por
     * aviso. Solo toca los que están en el buzón, así que la fecha del primer borrado se conserva.
     */
    @Override
    @Modifying
    @Transactional
    @Query("update Notification n set n.deletedAt = :when"
            + " where n.recipientId = :recipientId and n.deletedAt is null")
    int markAllAsDeleted(@Param("recipientId") Long recipientId, @Param("when") Instant when);
}
