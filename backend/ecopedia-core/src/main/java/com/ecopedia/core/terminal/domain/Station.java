package com.ecopedia.core.terminal.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * Estación de carga: el punto físico que publica un operador (RF04).
 *
 * <p>Sin lógica todavía: el commit semilla fija el mapeo contra las tablas que crea Flyway
 * y nada más. Las reglas de negocio viven en {@link TerminalService}.
 *
 * <p>La baja es lógica ({@code active}), no un {@code DELETE}: una estación puede tener
 * reservas y sesiones históricas colgando, y borrarla las dejaría huérfanas.
 */
@Entity
@Table(name = "stations")
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    /**
     * Operador dueño de la estación. Es un identificador suelto y no una relación JPA porque
     * la tabla de usuarios todavía no existe; la clave foránea se agrega con ECO-23.
     */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * Nodo de la distribuidora al que está conectada la estación, con el código que usa la
     * distribuidora (RF15). Opcional: sin nodo no hay a quién consultar antes de una carga rápida.
     */
    @Column(name = "node_id", length = 40)
    private String nodeId;

    @ElementCollection
    @CollectionTable(name = "station_photos", joinColumns = @JoinColumn(name = "station_id"))
    @Column(name = "url", nullable = false, length = 500)
    private List<String> photoUrls = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public List<String> getPhotoUrls() {
        return photoUrls;
    }

    public void setPhotoUrls(List<String> photoUrls) {
        this.photoUrls = photoUrls;
    }
}
