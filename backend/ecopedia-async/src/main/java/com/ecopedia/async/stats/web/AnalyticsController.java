package com.ecopedia.async.stats.web;

import com.ecopedia.async.security.AuthenticatedUser;
import com.ecopedia.async.stats.domain.AnalyticsService;
import com.ecopedia.async.stats.web.dto.AnalyticsResponse;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El tablero de Estadísticas por REST (ECO-301, RF16, RF19).
 *
 * <p>{@code ADMIN} ve toda la red ({@code scope = "NETWORK"}); {@code CPO} ve solo sus estaciones
 * ({@code scope = "OPERATOR"}). Sin {@code from} ni {@code to}, las últimas 24 horas; el rango no
 * puede pasar de 31 días.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CPO')")
    public AnalyticsResponse dashboard(
            Authentication authentication,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        Long ownerId = AuthenticatedUser.isAdmin(authentication) ? null : AuthenticatedUser.idOf(authentication);
        return AnalyticsResponse.fromDomain(analyticsService.getDashboard(ownerId, from, to));
    }
}
