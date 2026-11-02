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
     * La baja de usuarios ya no está acá (02/11): duplicaba DELETE /api/users/{id}, no la llamaba
     * nadie y, sin la contraseña del ADMIN, era una forma de saltearse esa confirmación.
     */

    /** Alta/Reactivación lógica administrativa de un usuario. */
    @PostMapping("/users/{id}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable Long id) {
        adminService.activateUser(id);
        return ResponseEntity.noContent().build();
    }

    /** Baja lógica administrativa de una estación de carga. */
    @DeleteMapping("/stations/{id}")
    public ResponseEntity<Void> deactivateStation(@PathVariable Long id) {
        adminService.deactivateStation(id);
        return ResponseEntity.noContent().build();
    }
}
