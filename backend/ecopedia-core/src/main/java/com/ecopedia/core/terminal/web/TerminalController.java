package com.ecopedia.core.terminal.web;

import com.ecopedia.core.terminal.domain.*;
import com.ecopedia.core.terminal.web.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Capa de presentación del componente {@code ServicioDeTerminales}.
 *
 * <p><b>Dónde va la autorización y por qué acá.</b> Las anotaciones {@code @PreAuthorize} van
 * sobre los métodos del controlador, no sobre los del servicio, y es deliberado: Reservas y
 * SesionesDeCarga consumen {@code TerminalService} en proceso —{@code getConnector} y
 * {@code changeOperationalStatus} están marcadas como operaciones internas en
 * ARQUITECTURA_ECOPEDIA.md §2.3—, y esas llamadas no las origina un usuario sino el sistema.
 * Con la regla en el servicio habría que inventarles un rol técnico para que pudieran pasar.
 * Acá el alcance queda en la puerta HTTP, que es exactamente lo que hay que proteger.
 *
 * <p><b>Por qué {@code 'CPO'} y no {@code 'OPERATOR'}.</b> {@code hasRole('X')} busca la
 * autoridad {@code ROLE_X}, y {@code JwtAuthenticationFilter} la arma como
 * {@code "ROLE_" + role.name()} sobre el enum {@link com.ecopedia.core.user.domain.Role}, que
 * dice {@code CPO}. El documento de arquitectura nombra el rol {@code OPERATOR}: la
 * divergencia entre documento y código es una decisión tomada, no un descuido.
 *
 * <p><b>Un operador toca solo lo suyo.</b> El rol no alcanza: sin el chequeo de dueño, cualquier
 * {@code CPO} editaba o borraba la estación de otro. Las operaciones sobre una estación o un
 * conector que ya existen piden ser {@code ADMIN} o ser el {@code CPO} dueño, y el dueño lo
 * resuelve {@link TerminalAccess} desde la misma anotación. El alta deja como dueño a quien la
 * hace. Si la estación no existe, el operador recibe 403 y no 404: no le confirma a nadie qué ids
 * hay.
 */
@RestController
@RequestMapping("/api")
public class TerminalController {

    private final TerminalService terminalService;
    private final TerminalAccess terminalAccess;

    public TerminalController(TerminalService terminalService, TerminalAccess terminalAccess) {
        this.terminalService = terminalService;
        this.terminalAccess = terminalAccess;
    }

    /** RF04: Alta de una estación de carga. */
    @PostMapping("/stations")
    @PreAuthorize("hasAnyRole('CPO','ADMIN') and @terminalAccess.userIdOf(authentication) != null")
    public ResponseEntity<StationResponse> createStation(
            Authentication authentication, @Valid @RequestBody StationRequest request) {
        Long ownerId = terminalAccess.userIdOf(authentication);
        Station created = terminalService.createStation(ownerId, request.toDomainData());
        return ResponseEntity.status(HttpStatus.CREATED).body(StationResponse.fromDomain(created));
    }

    /** RF04: Edición de datos de una estación. */
    @PutMapping("/stations/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CPO') and @terminalAccess.ownsStation(#id, authentication))")
    public ResponseEntity<StationResponse> updateStation(
            @PathVariable Long id, @Valid @RequestBody StationRequest request) {
        Station updated = terminalService.updateStation(id, request.toDomainData());
        return ResponseEntity.ok(StationResponse.fromDomain(updated));
    }

    /** RF04: Baja lógica de una estación. */
    @DeleteMapping("/stations/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CPO') and @terminalAccess.ownsStation(#id, authentication))")
    public ResponseEntity<Void> deactivateStation(@PathVariable Long id) {
        terminalService.deactivateStation(id);
        return ResponseEntity.noContent().build();
    }

    /** RF05: Alta de un conector sobre una estación existente. */
    @PostMapping("/stations/{stationId}/connectors")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CPO') and @terminalAccess.ownsStation(#stationId, authentication))")
    public ResponseEntity<ConnectorResponse> addConnector(
            @PathVariable Long stationId, @Valid @RequestBody ConfigureConnectorRequest request) {
        Connector created = terminalService.addConnector(stationId, request.connectorType(), request.maxPowerKw());
        return ResponseEntity.status(HttpStatus.CREATED).body(ConnectorResponse.fromDomain(created));
    }

    /** RF05: Parametrizar tipo y potencia máxima de un conector que ya existe. */
    @PostMapping("/connectors/{id}/configure")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CPO') and @terminalAccess.ownsConnector(#id, authentication))")
    public ResponseEntity<ConnectorResponse> configureConnector(
            @PathVariable Long id, @Valid @RequestBody ConfigureConnectorRequest request) {
        Connector configured = terminalService.configureConnector(id, request.connectorType(), request.maxPowerKw());
        return ResponseEntity.ok(ConnectorResponse.fromDomain(configured));
    }

    /** RF05: Cambiar el estado operativo de un conector. */
    @PatchMapping("/connectors/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CPO') and @terminalAccess.ownsConnector(#id, authentication))")
    public ResponseEntity<Void> changeOperationalStatus(
            @PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request) {
        terminalService.changeOperationalStatus(id, request.operationalStatus());
        return ResponseEntity.ok().build();
    }

    /** RF07: Búsqueda geolocalizada con filtros. */
    @GetMapping("/search")
    public ResponseEntity<List<StationResult>> search(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "10.0") @Positive(message = "El radio debe ser mayor que cero")
                    double radiusKm,
            @RequestParam(required = false) ConnectorType connectorType,
            @RequestParam(required = false) BigDecimal minimumPowerKw,
            @RequestParam(defaultValue = "false") boolean onlyAvailable) {

        SearchCriteria criteria = new SearchCriteria(lat, lon, radiusKm, connectorType, minimumPowerKw, onlyAvailable);
        List<StationResult> results = terminalService.search(criteria);
        return ResponseEntity.ok(results);
    }

    /** Listar todas las estaciones activas. */
    @GetMapping("/stations")
    public ResponseEntity<List<StationResponse>> getAllStations() {
        List<Station> stations = terminalService.getAllStations();
        return ResponseEntity.ok(stations.stream()
                .map(s -> StationResponse.fromDomain(
                        s,
                        terminalService.getConnectorsByStation(s.getId()).stream()
                                .map(ConnectorResponse::fromDomain)
                                .toList()))
                .toList());
    }

    /** Consultar una estación por ID. */
    @GetMapping("/stations/{id}")
    public ResponseEntity<StationResponse> getStation(@PathVariable Long id) {
        Station station = terminalService.getStation(id);
        List<ConnectorResponse> connectors = terminalService.getConnectorsByStation(id).stream()
                .map(ConnectorResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(StationResponse.fromDomain(station, connectors));
    }

    /** Listar conectores (opcionalmente por estación). */
    @GetMapping("/connectors")
    public ResponseEntity<List<ConnectorResponse>> getAllConnectors(@RequestParam(required = false) Long stationId) {
        List<Connector> connectors = (stationId != null)
                ? terminalService.getConnectorsByStation(stationId)
                : terminalService.getAllConnectors();
        return ResponseEntity.ok(
                connectors.stream().map(ConnectorResponse::fromDomain).toList());
    }

    /** Operación interna / consulta de conector por ID. */
    @GetMapping("/connectors/{id}")
    public ResponseEntity<ConnectorResponse> getConnector(@PathVariable Long id) {
        Connector connector = terminalService.getConnector(id);
        return ResponseEntity.ok(ConnectorResponse.fromDomain(connector));
    }

    /** Eliminar un conector por ID. */
    @DeleteMapping("/connectors/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CPO') and @terminalAccess.ownsConnector(#id, authentication))")
    public ResponseEntity<Void> removeConnector(@PathVariable Long id) {
        terminalService.removeConnector(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
