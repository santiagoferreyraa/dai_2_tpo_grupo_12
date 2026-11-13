package com.ecopedia.charging.session.data;

import com.ecopedia.charging.session.domain.ChargerEndReason;
import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.SessionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface JpaChargingSessionRepository extends JpaRepository<ChargingSession, Long>, ChargingSessionRepository {

    @Override
    Optional<ChargingSession> findBySessionId(String sessionId);

    @Override
    @Query(
            """
            select s from ChargingSession s
            where s.connectorId = :connectorId
              and s.status = com.ecopedia.charging.session.domain.SessionStatus.ACTIVE
            """)
    Optional<ChargingSession> findActiveByConnectorId(@Param("connectorId") Long connectorId);

    @Override
    @Query(
            """
            select s from ChargingSession s
            where s.driverId = :driverId
              and s.status = com.ecopedia.charging.session.domain.SessionStatus.ACTIVE
            """)
    Optional<ChargingSession> findActiveByDriverId(@Param("driverId") Long driverId);

    @Override
    List<ChargingSession> findByDriverId(Long driverId);

    @Override
    List<ChargingSession> findByStatus(SessionStatus status);

    /*
     * Una sentencia y no leer-modificar-guardar: las lecturas llegan cada pocos segundos mientras el
     * conductor puede estar deteniendo la carga, y con la entidad las dos escrituras chocarían por
     * el bloqueo optimista; el choque le daría un error al conductor por una lectura. Así la
     * lectura no toca la versión, y las condiciones —sesión activa, lectura no atrasada— se evalúan
     * en la base, en el mismo instante en que se escribe.
     */
    @Override
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
            """
            update ChargingSession s
            set s.kwhDelivered = case when :energyKwh > s.kwhDelivered then :energyKwh else s.kwhDelivered end,
                s.currentPowerKw = :powerKw,
                s.batteryPercent = :batteryPercent,
                s.lastReadingAt = :readingAt,
                s.chargerEndReason = coalesce(:endReason, s.chargerEndReason)
            where s.sessionId = :sessionId
              and s.status = com.ecopedia.charging.session.domain.SessionStatus.ACTIVE
              and (s.lastReadingAt is null or s.lastReadingAt <= :readingAt)
            """)
    int recordReading(
            @Param("sessionId") String sessionId,
            @Param("energyKwh") BigDecimal energyKwh,
            @Param("powerKw") BigDecimal powerKw,
            @Param("batteryPercent") BigDecimal batteryPercent,
            @Param("readingAt") Instant readingAt,
            @Param("endReason") ChargerEndReason endReason);
}
