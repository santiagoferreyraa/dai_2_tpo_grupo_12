package com.ecopedia.charging.session.web;

import com.ecopedia.charging.security.AuthenticatedUser;
import com.ecopedia.charging.session.domain.ActivationResult;
import com.ecopedia.charging.session.domain.ChargingSessionService;
import com.ecopedia.charging.session.domain.SessionStopResult;
import com.ecopedia.charging.session.web.dto.ActivationHttpRequest;
import com.ecopedia.charging.session.web.dto.ActivationHttpResponse;
import com.ecopedia.charging.session.web.dto.SessionHttpResponse;
import com.ecopedia.charging.session.web.dto.SessionStopResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para el ciclo de vida y habilitación de sesiones de carga (RF11, RNF06, ECO-60).
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final ChargingSessionService activationService;

    public SessionController(ChargingSessionService activationService) {
        this.activationService = activationService;
    }

    /**
     * Solicita la habilitación de una sesión de carga (Flujo B: escanear -> validar -> activar).
     */
    @PostMapping("/activation")
    @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<ActivationHttpResponse> activateSession(
            @AuthenticationPrincipal AuthenticatedUser driver,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @Valid @RequestBody ActivationHttpRequest request) {

        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;
        ActivationResult result = activationService.activate(request.toDomain(driver.id()), token);

        return ResponseEntity.status(HttpStatus.CREATED).body(ActivationHttpResponse.fromDomain(result));
    }

    /**
     * Detiene una sesión de carga activa, libera el conector, recorta la ventana remanente de la
     * reserva y liquida el consumo real descontando la seña (Flujo C, RF12, RF14, ECO-55).
     */
    /**
     * El conductor detiene su carga (RF11, ECO-44): apaga el cargador, libera el conector y liquida.
     * Sin cuerpo: los kWh los dice el cargador, no el conductor. Repetirlo devuelve lo mismo.
     */
    @PostMapping("/{sessionId}/stop")
    @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<SessionStopResponse> stopSession(
            @AuthenticationPrincipal AuthenticatedUser driver, @PathVariable String sessionId) {
        SessionStopResult result = activationService.stopSession(sessionId, driver.id());
        return ResponseEntity.ok(SessionStopResponse.fromDomain(result));
    }

    /**
     * La sesión activa del conductor, si tiene una (ECO-44): 200 con la sesión, o 204. Es lo primero
     * que pregunta la pantalla de carga, para no ofrecer escanear a quien ya está cargando.
     */
    @GetMapping("/active")
    @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<SessionHttpResponse> activeSession(@AuthenticationPrincipal AuthenticatedUser driver) {
        return activationService
                .findActiveSession(driver.id())
                .map(view -> ResponseEntity.ok(SessionHttpResponse.fromDomain(view)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Una sesión del conductor, activa o terminada (ECO-44). La de otro es el mismo 404 que una que no existe. */
    @GetMapping("/{sessionId}")
    @PreAuthorize("hasRole('CONDUCTOR')")
    public SessionHttpResponse getSession(
            @AuthenticationPrincipal AuthenticatedUser driver, @PathVariable String sessionId) {
        return SessionHttpResponse.fromDomain(activationService.getSession(sessionId, driver.id()));
    }
}
