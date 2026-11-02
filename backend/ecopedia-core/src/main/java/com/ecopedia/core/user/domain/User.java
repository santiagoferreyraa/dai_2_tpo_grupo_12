package com.ecopedia.core.user.domain;

import com.ecopedia.core.vehicle.domain.VehicleModel;
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
import jakarta.persistence.Table;
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
     * El auto del conductor, o {@code null} si todavía no eligió ninguno.
     *
     * <p><b>{@code null} es un estado legítimo y no un dato que falta.</b> Toda cuenta nace sin
     * auto y cualquiera puede sacárselo, así que las pantallas tienen que saber decir "todavía
     * no elegiste tu vehículo" en vez de dibujar una ficha con guiones.
     *
     * <p><b>{@code EAGER} y no {@code LAZY}, al revés que las otras relaciones del módulo.</b>
     * El perfil se lee de a uno y la ficha del auto se muestra junto con el nombre: con carga
     * diferida, armar {@code UserProfileResponse} en el controlador —ya fuera de la transacción
     * del servicio— reventaría al tocar el proxy. La alternativa sería mapear adentro del
     * servicio, que es meter la capa de presentación en la de negocio para ahorrar un JOIN sobre
     * una tabla de decenas de filas.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicle_model_id")
    private VehicleModel vehicleModel;

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

    public VehicleModel getVehicleModel() {
        return vehicleModel;
    }

    public void setVehicleModel(VehicleModel vehicleModel) {
        this.vehicleModel = vehicleModel;
    }
}
