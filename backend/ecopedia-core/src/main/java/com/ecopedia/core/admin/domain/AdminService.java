package com.ecopedia.core.admin.domain;

/**
 * Interfaz pública del componente Backoffice del Administrador (RF03 / ECO-27).
 *
 * <p>Permite consultar el estado general de la plataforma y ejecutar operaciones
 * de baja administrativa sobre usuarios y estaciones.
 */
public interface AdminService {

    /** Obtiene el resumen métrico consolidado del estado general de la plataforma. */
    AdminDashboard getDashboardSummary();

    /** Alta/Reactivación lógica administrativa de un usuario. */
    void activateUser(Long userId);

    /** Baja lógica administrativa de una estación. */
    void deactivateStation(Long stationId);
}
