package com.ecopedia.core.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * Entidad de Usuario del sistema (ECO-23).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 120)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /**
     * El id del auto del conductor en el catálogo de Vehículos, o {@code null} si todavía no eligió
     * ninguno.
     *
     * <p><b>{@code null} es un estado legítimo y no un dato que falta.</b> Toda cuenta nace sin
     * auto y cualquiera puede sacárselo, así que las pantallas tienen que saber decir "todavía
     * no elegiste tu vehículo" en vez de dibujar una ficha con guiones.
     *
     * <p><b>Solo el id, sin {@code @ManyToOne} ni clave foránea</b> (RNF09): Usuarios no mapea las
     * tablas de otro componente. Que el modelo exista lo valida {@code VehicleService} al
     * guardarlo, y la ficha la resuelve el mismo servicio al armar la respuesta.
     */
    @Column(name = "vehicle_model_id")
    private Long vehicleModelId;

    /**
     * Sube en cada cambio de la cuenta y la API la publica como ETag del perfil (QA-30). La pone
     * Hibernate, nunca el código; por eso no tiene setter.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getVehicleModelId() {
        return vehicleModelId;
    }

    public void setVehicleModelId(Long vehicleModelId) {
        this.vehicleModelId = vehicleModelId;
    }

    public long getVersion() {
        return version;
    }
}
