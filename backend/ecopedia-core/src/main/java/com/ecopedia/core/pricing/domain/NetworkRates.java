package com.ecopedia.core.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Los precios de la carga, iguales para toda la red (decidido por Manuel el 31/10).
 *
 * <p>Los fija el {@code ADMIN} y nadie más: el {@code CPO} define la seña y la hora pico de su estación,
 * no cuánto vale el kWh. Hay una sola fila, con id 1, y la tabla lo garantiza con un {@code CHECK}.
 */
@Entity
@Table(name = "network_rates")
public class NetworkRates {

    /** La única fila. */
    public static final int SINGLETON_ID = 1;

    @Id
    @Column(name = "id")
    private Integer id = SINGLETON_ID;

    @Column(name = "normal_kwh_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal normalKwhRate;

    @Column(name = "fast_kwh_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal fastKwhRate;

    @Column(name = "excess_penalty_per_min", nullable = false, precision = 10, scale = 2)
    private BigDecimal excessPenaltyPerMin;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Lo pide JPA. */
    protected NetworkRates() {}

    public NetworkRates(BigDecimal normalKwhRate, BigDecimal fastKwhRate, BigDecimal excessPenaltyPerMin, Instant at) {
        update(normalKwhRate, fastKwhRate, excessPenaltyPerMin, at);
    }

    public void update(BigDecimal normalKwhRate, BigDecimal fastKwhRate, BigDecimal excessPenaltyPerMin, Instant at) {
        this.normalKwhRate = normalKwhRate;
        this.fastKwhRate = fastKwhRate;
        this.excessPenaltyPerMin = excessPenaltyPerMin;
        this.updatedAt = at;
    }

    /** El precio base del kWh según el tipo de carga. */
    public BigDecimal kwhRateFor(ChargeSpeed speed) {
        return speed == ChargeSpeed.FAST ? fastKwhRate : normalKwhRate;
    }

    public BigDecimal getNormalKwhRate() {
        return normalKwhRate;
    }

    public BigDecimal getFastKwhRate() {
        return fastKwhRate;
    }

    public BigDecimal getExcessPenaltyPerMin() {
        return excessPenaltyPerMin;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
