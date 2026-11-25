package com.ecopedia.async.stats;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.async.notification.TestTokens;
import com.ecopedia.async.stats.domain.DemandSlot;
import com.ecopedia.async.stats.domain.DemandSlotRepository;
import com.ecopedia.async.web.ApiProblems;
import java.math.BigDecimal;
import java.time.Instant;
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
class AnalyticsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DemandSlotRepository slots;

    @Value("${ecopedia.jwt.secret}")
    private String secret;

    @Test
    @DisplayName("Sin token responde 401 Unauthorized")
    void anonymousReturns401() throws Exception {
        mockMvc.perform(get("/api/analytics")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Rol CONDUCTOR responde 404 opaco (SEC-06)")
    void driverReturnsOpaque404() throws Exception {
        mockMvc.perform(get("/api/analytics")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(secret, 99L, "CONDUCTOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(ApiProblems.NOT_FOUND_DETAIL))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("ADMIN ve toda la red (NETWORK) y CPO ve solo sus estaciones (OPERATOR)")
    void adminSeesNetworkAndCpoSeesOnlyOwnStations() throws Exception {
        // Un día propio, para no cruzarse con las franjas que escriben otros tests del contexto.
        Instant base = Instant.parse("2026-10-01T10:00:00Z");
        slots.save(slot(9101L, base, 9001L, "Estación CPO 700", 700L, "6.000"));
        slots.save(slot(9102L, base, 9001L, "Estación CPO 700", 700L, "2.000"));
        slots.save(slot(9201L, base.plusSeconds(900), 9002L, "Estación CPO 800", 800L, "3.000"));

        String from = base.toString();
        String to = base.plusSeconds(3600).toString();

        mockMvc.perform(get("/api/analytics")
                        .param("from", from)
                        .param("to", to)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(secret, 1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("NETWORK"))
                .andExpect(jsonPath("$.slotMinutes").value(15))
                .andExpect(jsonPath("$.totals.energyKwh").value(11.0))
                .andExpect(jsonPath("$.totals.peakSimultaneousPowerKw").value(32.0))
                .andExpect(jsonPath("$.stations[*].stationId", hasItem(9001)))
                .andExpect(jsonPath("$.stations[*].stationId", hasItem(9002)));

        mockMvc.perform(get("/api/analytics")
                        .param("from", from)
                        .param("to", to)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(secret, 700L, "CPO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("OPERATOR"))
                .andExpect(jsonPath("$.totals.energyKwh").value(8.0))
                .andExpect(jsonPath("$.stations[0].slots[0].demandKw").value(32.0))
                .andExpect(jsonPath("$.connectors[*].connectorId", hasItem(9101)))
                .andExpect(jsonPath("$.stations[*].stationId", not(hasItem(9002))));
    }

    @Test
    @DisplayName("from > to responde 400 Bad Request con code INVALID_DATE_RANGE")
    void invertedRangeReturns400() throws Exception {
        mockMvc.perform(get("/api/analytics")
                        .param("from", "2026-11-26T00:00:00Z")
                        .param("to", "2026-11-25T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(secret, 1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("Rango de más de 31 días responde 400 con code INVALID_DATE_RANGE")
    void tooLongRangeReturns400() throws Exception {
        mockMvc.perform(get("/api/analytics")
                        .param("from", "2026-09-01T00:00:00Z")
                        .param("to", "2026-11-25T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(secret, 1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("Parámetro from con formato inválido responde 400 con code INVALID_PARAMETER")
    void invalidDateParamReturns400() throws Exception {
        mockMvc.perform(get("/api/analytics")
                        .param("from", "no-es-fecha")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(secret, 1L, "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    private static DemandSlot slot(
            long connectorId, Instant start, long stationId, String stationName, long ownerId, String energy) {
        DemandSlot slot = DemandSlot.open(connectorId, start);
        slot.assignStation(stationId, stationName, ownerId, "CCS2");
        slot.addReading(new BigDecimal(energy), new BigDecimal("30.00"), true);
        return slot;
    }
}
