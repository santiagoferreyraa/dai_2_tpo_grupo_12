package com.ecopedia.core.admin.web;

import com.ecopedia.core.admin.domain.AdminDashboard;
import com.ecopedia.core.admin.domain.AdminService;
import com.ecopedia.core.admin.web.dto.AdminDashboardResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST del Backoffice de Administración (RF03 / ECO-27).
 *
 * <p>Todas las operaciones están protegidas con {@code @PreAuthorize("hasRole('ADMIN')")}.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** Consulta del estado general de la plataforma (métricas consolidadas). */
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboardSummary() {
        AdminDashboard dashboard = adminService.getDashboardSummary();
        return ResponseEntity.ok(AdminDashboardResponse.fromDomain(dashboard));
    }

    /*
     * Las bajas y la reactivación ya no están acá: duplicaban las rutas de cada componente y no las
     * llamaba nadie. La baja de usuarios se sacó el 02/11 (salteaba la contraseña del ADMIN); la
     * reactivación de usuarios y la baja de estaciones, el 28/11. Se usan POST /api/users/{id}/activate
     * y DELETE /api/stations/{id}.
     */
}
