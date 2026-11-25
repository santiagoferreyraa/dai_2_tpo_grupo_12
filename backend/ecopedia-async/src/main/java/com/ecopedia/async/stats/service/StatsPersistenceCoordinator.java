package com.ecopedia.async.stats.service;

import com.ecopedia.async.stats.domain.SessionUsageStat;
import com.ecopedia.async.stats.domain.SessionUsageStatRepository;
import com.ecopedia.async.stats.domain.StationOwnershipInfo;
import com.ecopedia.async.stats.domain.StatsTelemetryReading;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinador transaccional para persistir de forma atómica e idempotente cada lectura de
 * telemetría sin retener conexiones JDBC durante llamadas externas a {@code ecopedia-core}.
 */
@Component
public class StatsPersistenceCoordinator {

    private final SessionUsageStatRepository repository;

    public StatsPersistenceCoordinator(SessionUsageStatRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean upsertReading(StatsTelemetryReading reading, StationOwnershipInfo stationInfo) {
        Optional<SessionUsageStat> existingOpt = repository.findBySessionId(reading.sessionId());
        if (existingOpt.isEmpty()) {
            repository.save(SessionUsageStat.fromFirstReading(reading, stationInfo));
            return true;
        }

        SessionUsageStat existing = existingOpt.get();
        boolean enriched = false;
        if (existing.needsStationEnrichment() && stationInfo != null) {
            existing.enrichStation(stationInfo);
            enriched = true;
        }

        boolean applied = existing.applyReading(reading);
        if (applied || enriched) {
            repository.save(existing);
            return true;
        }
        return false;
    }
}
