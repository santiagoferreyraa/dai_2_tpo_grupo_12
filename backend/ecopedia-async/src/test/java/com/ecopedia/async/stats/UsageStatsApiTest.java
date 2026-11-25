package com.ecopedia.async.stats;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.async.notification.TestTokens;
import com.ecopedia.async.stats.domain.SessionUsageStat;
import com.ecopedia.async.stats.domain.SessionUsageStatRepository;
import com.ecopedia.async.stats.domain.StationOwnershipInfo;
import com.ecopedia.async.stats.domain.StatsTelemetryReading;
import com.ecopedia.async.web.ApiProblems;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class UsageStatsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionUsageStatRepository repository;

    @Value("${ecopedia.jwt.secret}")
    private String secret;

    @Test
    @DisplayName("Sin token responde 401 Unauthorized")
    void anonymousReturns401() throws Exception {
        mockMvc.perform(get("/api/stats/usage")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Rol CONDUCTOR responde 404 opaco (SEC-06)")
    void driverReturnsOpaque404() throws Exception {
        mockMvc.perform(get("/api/stats/usage")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(secret, 99L, "CONDUCTOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(ApiProblems.NOT_FOUND_DETAIL))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("ADMIN ve toda la red (NETWORK) y CPO ve solo sus estaciones (OPERATOR)")
    void adminSeesNetworkAndCpoSeesOnlyOwnStations() throws Exception {
        Instant base = Instant.parse("2026-11-25T10:00:00Z");
        String cpoSessionId = "api-cpo-" + UUID.randomUUID();
        String otherSessionId = "api-other-" + UUID.randomUUID();

        repository.save(SessionUsageStat.fromFirstReading(
                new StatsTelemetryReading(
                        "e-" + cpoSessionId,
                        StatsTelemetryReading.Type.FINISHED,
                        cpoSessionId,
                        1L,
                        base.plusSeconds(60),
                        new BigDecimal("45.00"),
                        new BigDecimal("18.500"),
                        new BigDecimal("80.00"),
                        StatsTelemetryReading.EndReason.STOPPED),
                new StationOwnershipInfo(101L, "Estación CPO 700", 700L, "CCS2")));

        repository.save(SessionUsageStat.fromFirstReading(
                new StatsTelemetryReading(
                        "e-" + otherSessionId,
                        StatsTelemetryReading.Type.READING,
                        otherSessionId,
                        2L,
                        base.plusSeconds(120),
                        new BigDecimal("22.00"),
                        new BigDecimal("7.500"),
                        new BigDecimal("40.00"),
                        null),
                new StationOwnershipInfo(102L, "Estación CPO 800", 800L, "TYPE2")));

        String from = base.toString();
        String to = base.plusSeconds(300).toString();

        mockMvc.perform(get("/api/stats/usage")
                        .param("from", from)
                        .param("to", to)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(secret, 1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("NETWORK"))
                .andExpect(jsonPath("$.recentSessions[*].sessionId", hasItem(cpoSessionId)))
                .andExpect(jsonPath("$.recentSessions[*].sessionId", hasItem(otherSessionId)));

        mockMvc.perform(get("/api/stats/usage")
                        .param("from", from)
                        .param("to", to)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(secret, 700L, "CPO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("OPERATOR"))
                .andExpect(jsonPath("$.totals.totalSessions").value(1))
                .andExpect(jsonPath("$.recentSessions[*].sessionId", hasItem(cpoSessionId)))
                .andExpect(jsonPath("$.recentSessions[*].sessionId", not(hasItem(otherSessionId))));
    }

    @Test
    @DisplayName("from > to responde 400 Bad Request con code INVALID_DATE_RANGE")
    void invertedRangeReturns400() throws Exception {
        mockMvc.perform(get("/api/stats/usage")
                        .param("from", "2026-11-26T00:00:00Z")
                        .param("to", "2026-11-25T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(secret, 1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("Parámetro from con formato inválido responde 400 con code INVALID_PARAMETER")
    void invalidDateParamReturns400() throws Exception {
        mockMvc.perform(get("/api/stats/usage")
                        .param("from", "no-es-fecha")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(secret, 1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }
}
