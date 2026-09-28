package com.ecopedia.async.notification.data;

import com.ecopedia.async.notification.domain.Notification;
import com.ecopedia.async.notification.domain.NotificationRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** {@link NotificationRepository} resuelto por Spring Data JPA sobre la tabla {@code notifications}. */
@Repository
public interface JpaNotificationRepository extends JpaRepository<Notification, Long>, NotificationRepository {

    @Override
    boolean existsByEventId(String eventId);

    @Override
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);
}
