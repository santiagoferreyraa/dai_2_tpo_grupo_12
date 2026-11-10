package com.ecopedia.charging.booking.messaging;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Los avisos pendientes de publicar (patrón DAO). Solo lo usa la mensajería de Reservas. */
public interface PendingNotificationRepository extends JpaRepository<PendingNotification, Long> {

    boolean existsByEventId(String eventId);

    /** Los más viejos primero, de a tandas: es el orden en que pasaron los hechos. */
    List<PendingNotification> findTop100ByOrderByCreatedAtAscIdAsc();
}
