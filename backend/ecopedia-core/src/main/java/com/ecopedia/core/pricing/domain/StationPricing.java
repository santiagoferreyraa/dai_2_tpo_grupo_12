package com.ecopedia.core.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lo que decide el {@code CPO} sobre el cobro de su estación: la seña para reservar y si se cobra
 * tarifa de hora pico (decidido por Manuel el 31/10).
 *
 * <p><b>Una estación sin fila tiene la configuración por omisión</b> ({@link #defaultsFor}): sin seña y
 * con hora pico. Así una estación recién creada se puede reservar antes de que su operador la configure.
 */
@Entity
@Table(name = "station_pricing")
public class StationPricing {

    @Id
    @Column(name = "station_id")
    private Long stationId;

    @Column(name = "deposit_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "peak_pricing_enabled", nullable = false)
    private boolean peakPricingEnabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Lo pide JPA. */
    protected StationPricing() {}

    public StationPricing(Long stationId, BigDecimal depositAmount, boolean peakPricingEnabled, Instant at) {
        this.stationId = stationId;
        update(depositAmount, peakPricingEnabled, at);
    }

    /** La configuración de una estación que nadie configuró todavía. No se guarda. */
    public static StationPricing defaultsFor(Long stationId) {
        return new StationPricing(stationId, BigDecimal.ZERO, true, Instant.EPOCH);
    }

    public void update(BigDecimal depositAmount, boolean peakPricingEnabled, Instant at) {
        this.depositAmount = depositAmount;
        this.peakPricingEnabled = peakPricingEnabled;
        this.updatedAt = at;
    }

    public Long getStationId() {
        return stationId;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public boolean isPeakPricingEnabled() {
        return peakPricingEnabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
