package com.ecopedia.charging.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Estado operativo local del conector gestionado dentro de la transacción declarativa (RNF06, ECO-60).
 *
 * <p>La columna {@code version} implementa bloqueo optimista para garantizar que dos conductores
 * no puedan obtener el mismo slot físico ante intentos de habilitación concurrentes.
 */
@Entity
@Table(name = "connector_states")
public class ConnectorState {

    @Id
    @Column(name = "connector_id", nullable = false)
    private Long connectorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConnectorOperationalStatus status = ConnectorOperationalStatus.AVAILABLE;

    @Column(name = "current_session_id", length = 64)
    private String currentSessionId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ConnectorState() {}

    public ConnectorState(Long connectorId, ConnectorOperationalStatus status, Instant updatedAt) {
        this.connectorId = Objects.requireNonNull(connectorId, "connectorId no puede ser null");
        this.status = Objects.requireNonNull(status, "status no puede ser null");
        this.updatedAt =
                Objects.requireNonNull(updatedAt, "updatedAt no puede ser null").truncatedTo(ChronoUnit.MICROS);
    }

    public static ConnectorState available(Long connectorId, Instant now) {
        return new ConnectorState(connectorId, ConnectorOperationalStatus.AVAILABLE, now);
    }

    public void occupy(String sessionId, Instant now) {
        this.status = ConnectorOperationalStatus.OCCUPIED;
        this.currentSessionId = sessionId;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void release(Instant now) {
        this.status = ConnectorOperationalStatus.AVAILABLE;
        this.currentSessionId = null;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void markOutOfService(Instant now) {
        this.status = ConnectorOperationalStatus.OUT_OF_SERVICE;
        this.currentSessionId = null;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public Long getConnectorId() {
        return connectorId;
    }

    public ConnectorOperationalStatus getStatus() {
        return status;
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public boolean isAvailable() {
        return status == ConnectorOperationalStatus.AVAILABLE;
    }

    public boolean isOccupied() {
        return status == ConnectorOperationalStatus.OCCUPIED;
    }

    public boolean isOutOfService() {
        return status == ConnectorOperationalStatus.OUT_OF_SERVICE;
    }
}
