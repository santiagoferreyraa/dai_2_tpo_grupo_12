package com.ecopedia.core.pricing.web;

import com.ecopedia.core.pricing.domain.ChargeBreakdown;
import com.ecopedia.core.pricing.domain.PricingRules;
import com.ecopedia.core.pricing.domain.PricingService;
import com.ecopedia.core.pricing.web.dto.DepositResponse;
import com.ecopedia.core.pricing.web.dto.EstimateCostResponse;
import com.ecopedia.core.pricing.web.dto.NetworkRatesRequest;
import com.ecopedia.core.pricing.web.dto.NetworkRatesResponse;
import com.ecopedia.core.pricing.web.dto.StationPricingRequest;
import com.ecopedia.core.pricing.web.dto.StationPricingResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Capa de presentación de <i>Tarificación</i> (RF06), con el modelo del 31/10.
 *
 * <p><b>Quién toca qué</b> (decidido por Manuel): los precios de la red, solo el {@code ADMIN}; la seña y
 * la hora pico de una estación, el {@code ADMIN} o el {@code CPO} dueño de esa estación. Las lecturas son
 * públicas: un precio se anuncia antes de comprometerse, como la búsqueda de estaciones.
 *
 * <p>Las rutas viejas por conector ({@code POST /api/pricing} y {@code GET /api/pricing/connector/{id}})
 * se borraron con el modelo por conector.
 */
@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final PricingService pricingService;
    private final PricingRules rules;
    private final Clock clock;

    public PricingController(PricingService pricingService, PricingRules rules, Clock clock) {
        this.pricingService = pricingService;
        this.rules = rules;
        this.clock = clock;
    }

    /** Los precios de la red y las reglas con que se aplican. */
    @GetMapping("/rates")
    public NetworkRatesResponse networkRates() {
        return NetworkRatesResponse.from(pricingService.getNetworkRates(), rules);
    }

    /** Cambia los precios de la red: carga normal, carga rápida y multa por minuto. Solo el ADMIN. */
    @PutMapping("/rates")
    @PreAuthorize("hasRole('ADMIN')")
    public NetworkRatesResponse updateNetworkRates(@Valid @RequestBody NetworkRatesRequest request) {
        return NetworkRatesResponse.from(
                pricingService.updateNetworkRates(
                        request.normalKwhRate(), request.fastKwhRate(), request.excessPenaltyPerMin()),
                rules);
    }

    /** La seña y la hora pico de una estación; si nadie las configuró, las de omisión. */
    @GetMapping("/stations/{stationId}")
    public StationPricingResponse stationPricing(@PathVariable Long stationId) {
        return StationPricingResponse.from(pricingService.getStationPricing(stationId));
    }

    /**
     * Cambia la seña y la hora pico de una estación. El ADMIN con cualquiera; el CPO, solo con las suyas,
     * con el mismo {@code TerminalAccess} que usa el ABM de Terminales.
     */
    @PutMapping("/stations/{stationId}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('CPO') and @terminalAccess.ownsStation(#stationId, authentication))")
    public StationPricingResponse updateStationPricing(
            @PathVariable Long stationId, @Valid @RequestBody StationPricingRequest request) {
        return StationPricingResponse.from(
                pricingService.updateStationPricing(stationId, request.depositAmount(), request.peakPricingEnabled()));
    }

    /**
     * RF06 / ECO-53: la seña que pide el conector para reservarlo: la de su estación. La consultan el
     * checkout (para cobrarla) y la pantalla de reservar (para anunciarla), y tiene que ser la misma
     * respuesta para los dos.
     *
     * <p>Un conector inexistente contesta 400, por el manejador de abajo, y quien llama lo lee como "sin
     * seña".
     */
    @GetMapping("/deposit")
    public DepositResponse deposit(@RequestParam Long connectorId) {
        return new DepositResponse(connectorId, pricingService.calculateDeposit(connectorId));
    }

    /**
     * RF06: lo que costaría cargar esa energía en ese conector, a su potencia máxima. Si no se dice
     * cuándo, empezando ahora; con {@code start} (ISO-8601), empezando entonces, que es lo que decide si es
     * hora pico.
     */
    @GetMapping("/estimate")
    public EstimateCostResponse estimateCost(
            @RequestParam Long connectorId,
            @RequestParam BigDecimal estimatedKwh,
            @RequestParam(required = false) Instant start) {
        ChargeBreakdown estimate =
                pricingService.estimateCost(connectorId, estimatedKwh, start != null ? start : clock.instant());
        return EstimateCostResponse.from(
                connectorId, estimatedKwh, pricingService.calculateDeposit(connectorId), estimate);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
