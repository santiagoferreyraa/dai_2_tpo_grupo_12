package com.ecopedia.core.vehicle.web;

import com.ecopedia.core.vehicle.domain.VehicleCatalog;
import com.ecopedia.core.vehicle.web.dto.VehicleModelResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El catálogo de modelos, para la pantalla que deja elegir el auto.
 *
 * <p><b>Sin {@code @PreAuthorize}, y es deliberado.</b> Es el único endpoint del módulo que no
 * pide sesión: la portada sin cuenta ofrece "entrá y guardá tu vehículo", y el listado de
 * modelos no dice nada de nadie —son fichas técnicas públicas, las mismas que están en el
 * folleto de cada fabricante—. Pedir token para leerlas obligaría a la pantalla de registro a
 * elegir entre no mostrar el auto o llamar con un token que todavía no tiene.
 *
 * <p>Solo lectura: el catálogo se completa por migración. Ver {@link VehicleCatalog}.
 */
@RestController
@RequestMapping("/api/vehicle-models")
public class VehicleModelController {

    private final VehicleCatalog vehicleCatalog;

    public VehicleModelController(VehicleCatalog vehicleCatalog) {
        this.vehicleCatalog = vehicleCatalog;
    }

    /**
     * El catálogo entero, ordenado por marca y modelo.
     *
     * <p>Sin paginado: son unas decenas de filas que la pantalla necesita completas para poder
     * agrupar por marca y buscar por texto sin ir y volver al servidor en cada tecla.
     */
    @GetMapping
    public ResponseEntity<List<VehicleModelResponse>> listModels() {
        List<VehicleModelResponse> models = vehicleCatalog.listAvailableModels().stream()
                .map(VehicleModelResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(models);
    }
}
