package com.ecopedia.integration.powergrid.web;

import com.ecopedia.integration.powergrid.domain.Feasibility;
import com.ecopedia.integration.powergrid.domain.PowerGridService;
import com.ecopedia.integration.powergrid.web.dto.FeasibilityRequest;
import com.ecopedia.integration.powergrid.web.dto.FeasibilityResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La puerta de RedEléctrica para SesionesDeCarga, que vive en otro artefacto (ECO-52).
 *
 * <p>El diseño decía "interfaz Java", y eso alcanzaba mientras los dos componentes compartieran
 * proceso: SesionesDeCarga está en {@code ecopedia-charging} y RedEléctrica acá. <b>Sigue sin haber
 * una API para usuarios:</b> esta ruta es {@code /internal/}, la protege la clave de servicio
 * ({@code InternalTokenFilter}) y no un rol, y el navegador no la alcanza.
 *
 * <p>Devuelve los tres resultados de {@link PowerGridService} sin decidir nada: un "no" de la red
 * es un 200 con {@code feasible = false}; un pedido que la distribuidora rechazó, 422 con el motivo;
 * una distribuidora que no contestó, 503. Qué hacer con cada uno lo decide SesionesDeCarga.
 */
@RestController
@RequestMapping("/internal/power-grid")
public class InternalPowerGridController {

    private final PowerGridService powerGrid;

    public InternalPowerGridController(PowerGridService powerGrid) {
        this.powerGrid = powerGrid;
    }

    @PostMapping("/feasibility")
    public FeasibilityResponse checkFeasibility(@Valid @RequestBody FeasibilityRequest request) {
        Feasibility feasibility = powerGrid.checkFeasibility(request.nodeId(), request.requestedPowerKw());
        return FeasibilityResponse.from(feasibility);
    }
}
