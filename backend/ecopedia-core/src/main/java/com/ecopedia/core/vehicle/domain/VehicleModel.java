package com.ecopedia.core.vehicle.domain;

import com.ecopedia.core.terminal.domain.ConnectorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * Un modelo de vehículo eléctrico del catálogo: la ficha técnica con la que el conductor
 * identifica su auto.
 *
 * <p><b>Es ficha de catálogo, no medición.</b> Todo lo que hay acá es cierto para cualquier
 * unidad del modelo y no cambia nunca: el conector que acepta, lo que entrega el motor, lo que
 * tolera al cargar. Lo que NO está —cuánta batería le queda hoy, cuánta autonomía tiene— son
 * lecturas del auto, y esta plataforma no habla con el auto. La distinción es la que decide qué
 * se puede mostrar y qué no.
 *
 * <p><b>Comparte el {@link ConnectorType} de Terminales en vez de tener el suyo.</b> Un enum
 * propio con los mismos tres valores obligaría a traducir en cada comparación, y la comparación
 * —¿este conector carga este auto?— es todo el motivo por el que el catálogo existe. Los dos
 * componentes viven en {@code ecopedia-core}, así que compartirlo no cruza ningún artefacto.
 */
@Entity
@Table(name = "vehicle_models")
public class VehicleModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "brand", nullable = false, length = 60)
    private String brand;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false, length = 20)
    private ConnectorType connectorType;

    /**
     * Potencia del motor en kW.
     *
     * <p>{@code BigDecimal} y no {@code double} por lo mismo que {@code Connector.maxPowerKw}:
     * son números que se muestran y se comparan contra los del conector, y el redondeo binario
     * se nota justo ahí.
     */
    @Column(name = "motor_kw", nullable = false, precision = 6, scale = 2)
    private BigDecimal motorKw;

    /**
     * Lo máximo que el auto acepta al cargar, en kW.
     *
     * <p>No es lo mismo que la potencia del cargador, y esa diferencia es lo que hace útil el
     * dato: en una estación de 180 kW un auto con tope de 50 igual carga a 50.
     */
    @Column(name = "max_charge_kw", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxChargeKw;

    /** Capacidad utilizable de la batería, en kWh. */
    @Column(name = "battery_kwh", nullable = false, precision = 6, scale = 2)
    private BigDecimal batteryKwh;

    /**
     * Cómo se llama el archivo de la foto de este modelo, sin carpeta ni extensión.
     *
     * <p><b>Es el nombre y no la dirección.</b> Guardar "/vehicles/models/tesla-model-3.png"
     * metería una ruta del frontend adentro de la base, y mudar las imágenes de carpeta pasaría a
     * ser una migración de datos en vez de un cambio en una función. Dónde vive la foto lo decide
     * quien la muestra.
     */
    @Column(name = "image_slug", nullable = false, length = 60)
    private String imageSlug;

    /**
     * Baja lógica. Un modelo discontinuado deja de ofrecerse en el selector, pero sigue siendo
     * el auto de quien ya lo eligió: borrar la fila dejaría ese perfil apuntando a la nada.
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConnectorType getConnectorType() {
        return connectorType;
    }

    public void setConnectorType(ConnectorType connectorType) {
        this.connectorType = connectorType;
    }

    public BigDecimal getMotorKw() {
        return motorKw;
    }

    public void setMotorKw(BigDecimal motorKw) {
        this.motorKw = motorKw;
    }

    public BigDecimal getMaxChargeKw() {
        return maxChargeKw;
    }

    public void setMaxChargeKw(BigDecimal maxChargeKw) {
        this.maxChargeKw = maxChargeKw;
    }

    public BigDecimal getBatteryKwh() {
        return batteryKwh;
    }

    public void setBatteryKwh(BigDecimal batteryKwh) {
        this.batteryKwh = batteryKwh;
    }

    public String getImageSlug() {
        return imageSlug;
    }

    public void setImageSlug(String imageSlug) {
        this.imageSlug = imageSlug;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
