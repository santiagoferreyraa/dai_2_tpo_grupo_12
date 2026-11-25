package com.ecopedia.async.stats.web;

import com.ecopedia.async.security.AuthenticatedUser;
import com.ecopedia.async.stats.domain.UsageStatsService;
import com.ecopedia.async.stats.web.dto.UsageSummaryResponse;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST del tablero de uso de estaciones y sesiones (ECO-301).
 *
 * <p>{@code ADMIN} consulta toda la red ({@code scope = "NETWORK"}); {@code CPO} consulta
 * únicamente las sesiones de sus propias estaciones ({@code scope = "OPERATOR"}).
 */
@RestController
@RequestMapping("/api/stats")
public class UsageStatsController {

    private final UsageStatsService usageStatsService;

    public UsageStatsController(UsageStatsService usageStatsService) {
        this.usageStatsService = usageStatsService;
    }

    @GetMapping("/usage")
    @PreAuthorize("hasAnyRole('ADMIN', 'CPO')")
    public UsageSummaryResponse usage(
            Authentication authentication,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        Long ownerId = AuthenticatedUser.isAdmin(authentication) ? null : AuthenticatedUser.idOf(authentication);
        return UsageSummaryResponse.fromDomain(usageStatsService.getUsageSummary(ownerId, from, to));
    }
}
