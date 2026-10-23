package com.ecopedia.core.pricing.web;

import com.ecopedia.core.pricing.domain.PricingScheme;
import com.ecopedia.core.pricing.domain.PricingService;
import com.ecopedia.core.pricing.web.dto.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    /**
     * RF06 / ECO-29: Definir esquema tarifario de un conector (ADMIN, o el CPO dueño del conector).
     *
     * <p>El dueño lo resuelve {@code TerminalAccess}, el mismo que usa el ABM de Terminales: sin
     * ese chequeo, cualquier operador le ponía precio al conector de otro.
     */
    @PostMapping
    @PreAuthorize(
            "hasRole('ADMIN') or (hasRole('CPO') and @terminalAccess.ownsConnector(#request.connectorId(), authentication))")
    public ResponseEntity<PricingSchemeResponse> defineScheme(@Valid @RequestBody PricingSchemeRequest request) {
        PricingScheme scheme = pricingService.defineScheme(request.toDomainData());
        return ResponseEntity.status(HttpStatus.CREATED).body(PricingSchemeResponse.fromDomain(scheme));
    }

    /** RF06: Consultar esquema tarifario vigente de un conector. */
    @GetMapping("/connector/{connectorId}")
    public ResponseEntity<PricingSchemeResponse> getScheme(@PathVariable Long connectorId) {
        PricingScheme scheme = pricingService.getSchemeForConnector(connectorId);
        return ResponseEntity.ok(PricingSchemeResponse.fromDomain(scheme));
    }

    /** RF06: Estimar importe proyectado para una reserva/carga. */
    @GetMapping("/estimate")
    public ResponseEntity<EstimateCostResponse> estimateCost(
            @RequestParam Long connectorId, @RequestParam BigDecimal estimatedKwh) {

        BigDecimal deposit = pricingService.calculateDeposit(connectorId);
        BigDecimal estimatedCost = pricingService.estimateCost(connectorId, estimatedKwh, LocalDateTime.now());

        return ResponseEntity.ok(new EstimateCostResponse(connectorId, estimatedKwh, deposit, estimatedCost));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
