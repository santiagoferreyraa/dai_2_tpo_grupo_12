package com.ecopedia.async.stats.service;

import com.ecopedia.async.stats.domain.DemandSlot;
import com.ecopedia.async.stats.domain.DemandSlotRepository;
import com.ecopedia.async.stats.domain.SessionUsageStat;
import com.ecopedia.async.stats.domain.SessionUsageStatRepository;
import com.ecopedia.async.stats.domain.StationOwnershipInfo;
import com.ecopedia.async.stats.domain.StatsTelemetryReading;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste cada lectura de forma atómica e idempotente, sin retener conexiones JDBC durante las
 * llamadas a {@code ecopedia-core} (esas las hace antes {@link AnalyticsServiceImpl}).
 *
 * <p>En la misma transacción actualiza dos cosas:
 *
 * <ul>
 *   <li>{@link SessionUsageStat}, el cursor de la sesión: el último acumulado de energía y la
 *       última lectura vista. Sirve para pasar del acumulado que manda el cargador a lo que se
 *       cargó desde la lectura anterior, y para descartar duplicados y atrasadas.
 *   <li>{@link DemandSlot}, el agregado por conector y franja de 15 minutos, que es lo que lee el
 *       tablero.
 * </ul>
 *
 * <p>Si algo falla, se deshacen las dos juntas y Artemis vuelve a entregar la lectura: no queda
 * energía sumada en la franja sin que el cursor haya avanzado, ni al revés.
 */
@Component
public class StatsPersistenceCoordinator {

    private final SessionUsageStatRepository sessions;
    private final DemandSlotRepository slots;

    public StatsPersistenceCoordinator(SessionUsageStatRepository sessions, DemandSlotRepository slots) {
        this.sessions = sessions;
        this.slots = slots;
    }

    @Transactional
    public boolean upsertReading(StatsTelemetryReading reading, StationOwnershipInfo stationInfo) {
        Optional<SessionUsageStat> existingOpt = sessions.findBySessionId(reading.sessionId());
        if (existingOpt.isEmpty()) {
            SessionUsageStat created = sessions.save(SessionUsageStat.fromFirstReading(reading, stationInfo));
            addToSlot(reading, created, created.getEnergyKwh(), true);
            return true;
        }

        SessionUsageStat existing = existingOpt.get();
        boolean enriched = false;
        if (existing.needsStationEnrichment() && stationInfo != null) {
            existing.enrichStation(stationInfo);
            backfillStation(existing);
            enriched = true;
        }

        BigDecimal energyBefore = existing.getEnergyKwh();
        boolean applied = existing.applyReading(reading);
        if (applied) {
            addToSlot(reading, existing, existing.getEnergyKwh().subtract(energyBefore), false);
        }
        if (applied || enriched) {
            sessions.save(existing);
            return true;
        }
        return false;
    }

    private void addToSlot(
            StatsTelemetryReading reading, SessionUsageStat session, BigDecimal energyDelta, boolean sessionStarted) {
        DemandSlot slot = slots.findByConnectorIdAndSlotStart(
                        reading.connectorId(), DemandSlot.startOf(reading.timestamp()))
                .orElseGet(() -> DemandSlot.open(reading.connectorId(), reading.timestamp()));
        assignStation(slot, session);
        slot.addReading(energyDelta, reading.powerKw(), sessionStarted);
        slots.save(slot);
    }

    /**
     * Las franjas que este conector abrió mientras core no respondía quedaron sin estación, y sin
     * estación el operador no las ve. El conector no cambia de estación, así que alcanza con
     * completarlas la primera vez que core contesta.
     */
    private void backfillStation(SessionUsageStat session) {
        for (DemandSlot slot : slots.findByConnectorIdAndStationIdIsNull(session.getConnectorId())) {
            assignStation(slot, session);
            slots.save(slot);
        }
    }

    private static void assignStation(DemandSlot slot, SessionUsageStat session) {
        slot.assignStation(
                session.getStationId(),
                session.getStationName(),
                session.getStationOwnerId(),
                session.getConnectorType());
    }
}
