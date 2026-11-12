package com.ecopedia.charging.session.domain;

import com.ecopedia.charging.checkout.domain.ConsumptionPaymentStatus;
import java.util.List;
import java.util.Optional;

public interface ChargingSessionRepository {

    ChargingSession save(ChargingSession session);

    Optional<ChargingSession> findById(Long id);

    Optional<ChargingSession> findBySessionId(String sessionId);

    Optional<ChargingSession> findActiveByConnectorId(Long connectorId);

    List<ChargingSession> findByDriverId(Long driverId);

    List<ChargingSession> findByStatus(SessionStatus status);

    List<ChargingSession> findByStatusAndPaymentStatus(SessionStatus status, ConsumptionPaymentStatus paymentStatus);
}
