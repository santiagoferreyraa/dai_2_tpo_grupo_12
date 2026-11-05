package com.ecopedia.charging.session.data;

import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.domain.ChargingSessionRepository;
import com.ecopedia.charging.session.domain.SessionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
    List<ChargingSession> findByDriverId(Long driverId);

    @Override
    List<ChargingSession> findByStatus(SessionStatus status);
}
