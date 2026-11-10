package com.ecopedia.core.terminal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Conector de una estación: la unidad que efectivamente se reserva y se carga (RF05).
 *
 * <p>Es el objeto que consumen Reservas y SesionesDeCarga a través de
 * {@link TerminalService#getConnector(Long)}. Su estado operativo es el que decide si una
 * habilitación puede prosperar.
 */
@Entity
@Table(name = "connectors")
public class Connector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false, length = 20)
    private ConnectorType connectorType;

    /**
     * Potencia máxima entregable en kW. {@code BigDecimal} y no {@code double} porque es un
     * valor que después entra en el cálculo de tarifas, donde el redondeo binario se nota.
     */
    @Column(name = "max_power_kw", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxPowerKw;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false, length = 20)
    private OperationalStatus operationalStatus = OperationalStatus.AVAILABLE;

    /**
     * El PIN de 6 dígitos impreso en la terminal, que también va en su QR (RF11, ECO-44). Acredita
     * que el conductor está frente a este conector. No sale por la API pública: lo ve el operador
     * dueño y lo resuelve SesionesDeCarga por una ruta interna. Ver {@link ConnectorAccessCode}.
     */
    @Column(name = "access_code", nullable = false, unique = true, length = ConnectorAccessCode.LENGTH)
    private String accessCode;

    /**
     * Red de seguridad: un conector que se guarda sin código recibe uno al azar. El camino normal es
     * {@code TerminalServiceImpl#addConnector}, que además verifica que no esté tomado; esto cubre
     * a quien arme un conector a mano (los tests, un script) para que la columna NOT NULL no lo rechace.
     */
    @PrePersist
    void assignAccessCode() {
        if (accessCode == null) {
            accessCode = ConnectorAccessCode.random();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Station getStation() {
        return station;
    }

    public void setStation(Station station) {
        this.station = station;
    }

    public ConnectorType getConnectorType() {
        return connectorType;
    }

    public void setConnectorType(ConnectorType connectorType) {
        this.connectorType = connectorType;
    }

    public BigDecimal getMaxPowerKw() {
        return maxPowerKw;
    }

    public void setMaxPowerKw(BigDecimal maxPowerKw) {
        this.maxPowerKw = maxPowerKw;
    }

    public OperationalStatus getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(OperationalStatus operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }
}
