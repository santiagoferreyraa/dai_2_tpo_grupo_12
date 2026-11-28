package com.ecopedia.core.admin.domain;

/**
 * Interfaz pública del componente Backoffice del Administrador (RF03 / ECO-27).
 *
 * <p>Solo el tablero: el estado general de la plataforma. Las bajas, reactivaciones y cambios de
 * rol las hace el {@code ADMIN} con las rutas de cada componente ({@code /api/users},
 * {@code /api/stations}), que son las que tienen las reglas (la contraseña de confirmación, las
 * cuentas {@code ADMIN} protegidas). Hasta el 28/11 había una copia de dos de ellas acá, sin esas
 * reglas, que no llamaba nadie.
 */
public interface AdminService {

    /** Obtiene el resumen métrico consolidado del estado general de la plataforma. */
    AdminDashboard getDashboardSummary();
}
