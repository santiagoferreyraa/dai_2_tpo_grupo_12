package com.ecopedia.simulation.charger.web;

import com.ecopedia.simulation.charger.domain.ChargerException;
import com.ecopedia.simulation.charger.service.ChargeOrder;
import com.ecopedia.simulation.charger.service.ChargerSimulator;
import com.ecopedia.simulation.charger.service.ChargerSimulator.Activation;
import com.ecopedia.simulation.charger.service.ConnectorView;
import com.ecopedia.simulation.charger.service.ConnectorView.SessionView;
import com.ecopedia.simulation.charger.web.dto.StartSessionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las órdenes que acepta el cargador.
 *
 * <ul>
 *   <li>{@code POST /v1/connectors/{id}/session} — encender: 201 si lo encendió, 200 si ya estaba
 *       cargando esa misma sesión.
 *   <li>{@code DELETE /v1/connectors/{id}/session} — apagar: 200 con cómo terminó, o 204 si no
 *       estaba cargando.
 *   <li>{@code GET /v1/connectors/{id}} — en qué está.
 *   <li>{@code POST} y {@code DELETE /v1/connectors/{id}/fault} — romperlo y repararlo, para la demo.
 * </ul>
 */
@RestController
@RequestMapping("/v1/connectors/{connectorId}")
public class ConnectorController {

    private final ChargerSimulator simulator;

    public ConnectorController(ChargerSimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping("/session")
    public ResponseEntity<ConnectorView> start(
            @PathVariable long connectorId, @Valid @RequestBody StartSessionRequest request) {
        Activation activation = simulator.activate(
                validated(connectorId),
                new ChargeOrder(
                        request.sessionId(),
                        request.powerLimitKw(),
                        request.batteryCapacityKwh(),
                        request.vehicleMaxChargeKw(),
                        request.initialBatteryPercent()));
        return ResponseEntity.status(activation.started() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(activation.connector());
    }

    @DeleteMapping("/session")
    public ResponseEntity<SessionView> stop(@PathVariable long connectorId) {
        return simulator
                .deactivate(validated(connectorId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    public ConnectorView status(@PathVariable long connectorId) {
        return simulator.status(validated(connectorId));
    }

    @PostMapping("/fault")
    public ConnectorView breakConnector(@PathVariable long connectorId) {
        return simulator.breakConnector(validated(connectorId));
    }

    @DeleteMapping("/fault")
    public ConnectorView repair(@PathVariable long connectorId) {
        return simulator.repair(validated(connectorId));
    }

    private static long validated(long connectorId) {
        if (connectorId <= 0) {
            throw new ChargerException(HttpStatus.BAD_REQUEST, "invalid_connector", "Connector id must be positive");
        }
        return connectorId;
    }
}
