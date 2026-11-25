package com.ecopedia.async.stats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Proyección de uso por sesión construida de forma idempotente desde el tópico {@code
 * telemetry.charging} (ECO-301, RF16).
 */
@Entity
@Table(name = "session_usage_stats")
public class SessionUsageStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(name = "connector_id", nullable = false)
    private Long connectorId;

    @Column(name = "station_id")
    private Long stationId;

    @Column(name = "station_name", length = 160)
    private String stationName;

    @Column(name = "station_owner_id")
    private Long stationOwnerId;

    @Column(name = "connector_type", length = 32)
    private String connectorType;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_reading_at", nullable = false)
    private Instant lastReadingAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "energy_kwh", nullable = false, precision = 10, scale = 3)
    private BigDecimal energyKwh;

    @Column(name = "peak_power_kw", nullable = false, precision = 8, scale = 2)
    private BigDecimal peakPowerKw;

    @Column(name = "last_power_kw", nullable = false, precision = 8, scale = 2)
    private BigDecimal lastPowerKw;

    @Column(name = "last_battery_percent", precision = 5, scale = 2)
    private BigDecimal lastBatteryPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UsageSessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 30)
    private StatsTelemetryReading.EndReason endReason;

    @Column(name = "readings_count", nullable = false)
    private int readingsCount;

    @Column(name = "last_event_id", length = 64)
    private String lastEventId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected SessionUsageStat() {}

    private SessionUsageStat(
            String sessionId,
            Long connectorId,
            Instant startedAt,
            BigDecimal energyKwh,
            BigDecimal powerKw,
            BigDecimal batteryPercent,
            UsageSessionStatus status,
            Instant endedAt,
            StatsTelemetryReading.EndReason endReason,
            String lastEventId) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId no puede ser null");
        this.connectorId = Objects.requireNonNull(connectorId, "connectorId no puede ser null");
        this.startedAt =
                Objects.requireNonNull(startedAt, "startedAt no puede ser null").truncatedTo(ChronoUnit.MICROS);
        this.lastReadingAt = this.startedAt;
        this.energyKwh = energyKwh;
        this.peakPowerKw = powerKw;
        this.lastPowerKw = powerKw;
        this.lastBatteryPercent = batteryPercent;
        this.status = status;
        this.endedAt = endedAt != null ? endedAt.truncatedTo(ChronoUnit.MICROS) : null;
        this.endReason = endReason;
        this.readingsCount = 1;
        this.lastEventId = lastEventId;
    }

    /** Crea la proyección inicial a partir de la primera lectura recibida para una sesión. */
    public static SessionUsageStat fromFirstReading(StatsTelemetryReading reading, StationOwnershipInfo stationInfo) {
        Objects.requireNonNull(reading, "reading no puede ser null");
        Instant ts = reading.timestamp().truncatedTo(ChronoUnit.MICROS);
        BigDecimal energy = nonNegativeOrZero(reading.energyKwh());
        BigDecimal power = nonNegativeOrZero(reading.powerKw());

        UsageSessionStatus initialStatus = UsageSessionStatus.ACTIVE;
        Instant ended = null;
        StatsTelemetryReading.EndReason reason = null;
        if (reading.type() == StatsTelemetryReading.Type.FINISHED) {
            initialStatus = UsageSessionStatus.FINISHED;
            ended = ts;
            reason = reading.endReason() != null ? reading.endReason() : StatsTelemetryReading.EndReason.STOPPED;
        } else if (reading.type() == StatsTelemetryReading.Type.FAULT) {
            initialStatus = UsageSessionStatus.FAULTED;
            ended = ts;
            reason = reading.endReason() != null ? reading.endReason() : StatsTelemetryReading.EndReason.FAULT;
        }

        SessionUsageStat stat = new SessionUsageStat(
                reading.sessionId(),
                reading.connectorId(),
                ts,
                energy,
                power,
                reading.batteryPercent(),
                initialStatus,
                ended,
                reason,
                reading.eventId());
        stat.enrichStation(stationInfo);
        return stat;
    }

    /**
     * Aplica una lectura subsiguiente de forma idempotente y monótona.
     *
     * @return {@code true} si la lectura produjo cambios relevantes; {@code false} si fue duplicada
     *     o atrasada.
     */
    public boolean applyReading(StatsTelemetryReading reading) {
        Objects.requireNonNull(reading, "reading no puede ser null");
        if (reading.eventId() != null && reading.eventId().equals(this.lastEventId)) {
            return false;
        }

        boolean isTerminalReading = reading.type() == StatsTelemetryReading.Type.FINISHED
                || reading.type() == StatsTelemetryReading.Type.FAULT;
        boolean alreadyTerminal =
                this.status == UsageSessionStatus.FINISHED || this.status == UsageSessionStatus.FAULTED;
        if (alreadyTerminal && !isTerminalReading) {
            return false;
        }

        Instant readingTs = reading.timestamp().truncatedTo(ChronoUnit.MICROS);
        if (readingTs.isBefore(this.lastReadingAt) && (!isTerminalReading || alreadyTerminal)) {
            return false;
        }

        this.readingsCount += 1;
        this.lastEventId = reading.eventId();

        if (!readingTs.isBefore(this.lastReadingAt)) {
            this.lastReadingAt = readingTs;
            this.lastPowerKw = nonNegativeOrZero(reading.powerKw());
            if (reading.batteryPercent() != null) {
                this.lastBatteryPercent = reading.batteryPercent();
            }
        }

        BigDecimal incomingEnergy = nonNegativeOrZero(reading.energyKwh());
        if (incomingEnergy.compareTo(this.energyKwh) > 0) {
            this.energyKwh = incomingEnergy;
        }

        BigDecimal incomingPower = nonNegativeOrZero(reading.powerKw());
        if (incomingPower.compareTo(this.peakPowerKw) > 0) {
            this.peakPowerKw = incomingPower;
        }

        if (reading.type() == StatsTelemetryReading.Type.FINISHED) {
            this.status = UsageSessionStatus.FINISHED;
            this.endedAt = readingTs;
            this.endReason =
                    reading.endReason() != null ? reading.endReason() : StatsTelemetryReading.EndReason.STOPPED;
        } else if (reading.type() == StatsTelemetryReading.Type.FAULT) {
            this.status = UsageSessionStatus.FAULTED;
            this.endedAt = readingTs;
            this.endReason = reading.endReason() != null ? reading.endReason() : StatsTelemetryReading.EndReason.FAULT;
        }

        return true;
    }

    /** Completa los datos de estación y propietario si estaban pendientes y llegaron desde core. */
    public void enrichStation(StationOwnershipInfo stationInfo) {
        if (stationInfo == null) {
            return;
        }
        this.stationId = stationInfo.stationId();
        this.stationName = stationInfo.stationName();
        this.stationOwnerId = stationInfo.ownerId();
        this.connectorType = stationInfo.connectorType();
    }

    public boolean needsStationEnrichment() {
        return this.stationId == null;
    }

    private static BigDecimal nonNegativeOrZero(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getConnectorId() {
        return connectorId;
    }

    public Long getStationId() {
        return stationId;
    }

    public String getStationName() {
        return stationName;
    }

    public Long getStationOwnerId() {
        return stationOwnerId;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastReadingAt() {
        return lastReadingAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public BigDecimal getEnergyKwh() {
        return energyKwh;
    }

    public BigDecimal getPeakPowerKw() {
        return peakPowerKw;
    }

    public BigDecimal getLastPowerKw() {
        return lastPowerKw;
    }

    public BigDecimal getLastBatteryPercent() {
        return lastBatteryPercent;
    }

    public UsageSessionStatus getStatus() {
        return status;
    }

    public StatsTelemetryReading.EndReason getEndReason() {
        return endReason;
    }

    public int getReadingsCount() {
        return readingsCount;
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public Long getVersion() {
        return version;
    }
}
