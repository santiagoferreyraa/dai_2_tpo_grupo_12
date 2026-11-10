package com.ecopedia.charging.session.domain;

import java.util.List;
import java.util.Optional;

public interface ChargingSessionRepository {

    ChargingSession save(ChargingSession session);

    Optional<ChargingSession> findById(Long id);

    Optional<ChargingSession> findBySessionId(String sessionId);

    Optional<ChargingSession> findActiveByConnectorId(Long connectorId);

    /** La sesión activa del conductor (ECO-44): tiene a lo sumo una. */
    Optional<ChargingSession> findActiveByDriverId(Long driverId);

    List<ChargingSession> findByDriverId(Long driverId);

    List<ChargingSession> findByStatus(SessionStatus status);
}
