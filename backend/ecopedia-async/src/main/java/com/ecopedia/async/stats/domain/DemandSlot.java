package com.ecopedia.async.stats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Lo que entregó un conector en una franja de 15 minutos (ECO-301, RF16, RF19).
 *
 * <p>Es el agregado que pide la ficha de Estadísticas: no se guardan las lecturas, se suman en la
 * franja que les toca. De acá salen la demanda por estación y franja, el pico de potencia simultánea
 * y la potencia entregada por conector.
 */
@Entity
@Table(name = "connector_demand_slots")
public class DemandSlot {

    /** El ancho de la franja. 15 minutos es el intervalo con el que se mide la demanda eléctrica. */
    public static final Duration SLOT = Duration.ofMinutes(15);

    private static final long SLOT_SECONDS = SLOT.toSeconds();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "connector_id", nullable = false)
    private Long connectorId;

    @Column(name = "slot_start", nullable = false)
    private Instant slotStart;

    @Column(name = "station_id")
    private Long stationId;

    @Column(name = "station_name", length = 160)
    private String stationName;

    @Column(name = "station_owner_id")
    private Long stationOwnerId;

    @Column(name = "connector_type", length = 32)
    private String connectorType;

    @Column(name = "energy_kwh", nullable = false, precision = 10, scale = 3)
    private BigDecimal energyKwh = BigDecimal.ZERO;

    @Column(name = "peak_power_kw", nullable = false, precision = 8, scale = 2)
    private BigDecimal peakPowerKw = BigDecimal.ZERO;

    @Column(name = "sessions_started", nullable = false)
    private int sessionsStarted;

    @Column(name = "readings_count", nullable = false)
    private int readingsCount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected DemandSlot() {}

    private DemandSlot(Long connectorId, Instant slotStart) {
        this.connectorId = Objects.requireNonNull(connectorId, "connectorId no puede ser null");
        this.slotStart = Objects.requireNonNull(slotStart, "slotStart no puede ser null");
    }

    /** Una franja vacía del conector, la que contiene a {@code instant}. */
    public static DemandSlot open(Long connectorId, Instant instant) {
        return new DemandSlot(connectorId, startOf(instant));
    }

    /** El comienzo de la franja de 15 minutos que contiene a {@code instant}. */
    public static Instant startOf(Instant instant) {
        long epoch = instant.getEpochSecond();
        return Instant.ofEpochSecond(epoch - Math.floorMod(epoch, SLOT_SECONDS));
    }

    /**
     * Suma una lectura a la franja.
     *
     * @param energyDeltaKwh lo que la sesión cargó desde la lectura anterior (no el acumulado).
     * @param powerKw la potencia que informó la lectura.
     * @param sessionStarted si la lectura es la primera de su sesión.
     */
    public void addReading(BigDecimal energyDeltaKwh, BigDecimal powerKw, boolean sessionStarted) {
        if (energyDeltaKwh != null && energyDeltaKwh.signum() > 0) {
            this.energyKwh = this.energyKwh.add(energyDeltaKwh);
        }
        if (powerKw != null && powerKw.compareTo(this.peakPowerKw) > 0) {
            this.peakPowerKw = powerKw;
        }
        if (sessionStarted) {
            this.sessionsStarted += 1;
        }
        this.readingsCount += 1;
    }

    /** Completa la estación si todavía no se sabía (core no respondía cuando se abrió la franja). */
    public void assignStation(Long stationId, String stationName, Long ownerId, String connectorType) {
        if (this.stationId != null || stationId == null) {
            return;
        }
        this.stationId = stationId;
        this.stationName = stationName;
        this.stationOwnerId = ownerId;
        this.connectorType = connectorType;
    }

    public Long getId() {
        return id;
    }

    public Long getConnectorId() {
        return connectorId;
    }

    public Instant getSlotStart() {
        return slotStart;
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

    public BigDecimal getEnergyKwh() {
        return energyKwh;
    }

    public BigDecimal getPeakPowerKw() {
        return peakPowerKw;
    }

    public int getSessionsStarted() {
        return sessionsStarted;
    }

    public int getReadingsCount() {
        return readingsCount;
    }
}
