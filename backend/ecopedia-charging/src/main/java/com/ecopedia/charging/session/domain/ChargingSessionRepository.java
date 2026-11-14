package com.ecopedia.charging.session.domain;

import java.math.BigDecimal;
import java.time.Instant;
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

    /**
     * Guarda la última lectura del cargador en una sesión activa, en una sola sentencia.
     *
     * <p>No pisa nada si la sesión no existe, ya cerró, o si ya se guardó una lectura posterior. Los
     * kWh nunca bajan: son acumulados.
     *
     * @return cuántas sesiones actualizó: 1 si la lectura se guardó, 0 si se descartó
     */
    int recordReading(
            String sessionId,
            BigDecimal energyKwh,
            BigDecimal powerKw,
            BigDecimal batteryPercent,
            Instant readingAt,
            ChargerEndReason endReason);
}
