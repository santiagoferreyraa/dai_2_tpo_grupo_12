package com.ecopedia.charging.session.web;

import com.ecopedia.charging.security.AuthenticatedUser;
import com.ecopedia.charging.session.domain.ActivationResult;
import com.ecopedia.charging.session.service.ChargingActivationService;
import com.ecopedia.charging.session.web.dto.ActivationHttpRequest;
import com.ecopedia.charging.session.web.dto.ActivationHttpResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    private final ChargingActivationService activationService;

    public SessionController(ChargingActivationService activationService) {
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
}
