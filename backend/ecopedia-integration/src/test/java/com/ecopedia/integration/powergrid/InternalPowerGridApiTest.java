package com.ecopedia.integration.powergrid;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.integration.powergrid.domain.Feasibility;
import com.ecopedia.integration.powergrid.domain.Feasibility.Restriction;
import com.ecopedia.integration.powergrid.domain.PowerGridPort;
import com.ecopedia.integration.powergrid.domain.PowerGridRequestRejectedException;
import com.ecopedia.integration.powergrid.domain.PowerGridRequestRejectedException.Reason;
import com.ecopedia.integration.powergrid.domain.PowerGridUnavailableException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * La ruta interna de RedEléctrica, con el artefacto entero y la distribuidora reemplazada por el
 * puerto: lo que se prueba es quién entra y cómo sale cada resultado, no el SOAP (eso lo prueban
 * {@code PowerGridWsClientTest} y el test en vivo).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class InternalPowerGridApiTest {

    private static final String SERVICE_TOKEN = "Bearer ecopedia-dev-only-internal-token";
    private static final String BODY = "{\"nodeId\":\"CABA-OESTE\",\"requestedPowerKw\":150}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PowerGridPort powerGrid;

    @Value("${ecopedia.jwt.secret}")
    private String jwtSecret;

    private ResultActions ask(String authorization, String body) throws Exception {
        var request = post("/internal/power-grid/feasibility")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (authorization != null) request.header(HttpHeaders.AUTHORIZATION, authorization);
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("Un \"no\" de la red es un 200, con el tope que el nodo sí acepta y el motivo")
    void answers() throws Exception {
        when(powerGrid.checkFeasibility(eq("CABA-OESTE"), any()))
                .thenReturn(new Feasibility(false, new BigDecimal("50"), Restriction.DEMAND_CAP_EXCEEDED));

        ask(SERVICE_TOKEN, BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible").value(false))
                .andExpect(jsonPath("$.maxAllowedPowerKw").value(50))
                .andExpect(jsonPath("$.reason").value("DEMAND_CAP_EXCEEDED"));
    }

    @Test
    @DisplayName("Un Fault vuelve como 422 con el motivo, y la distribuidora caída como 503")
    void failures() throws Exception {
        when(powerGrid.checkFeasibility(anyString(), any()))
                .thenThrow(new PowerGridRequestRejectedException(Reason.UNKNOWN_NODE, "x", null));
        ask(SERVICE_TOKEN, BODY)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_NODE"));

        // doThrow y no when(): re-stubear con when() llama al mock, que ya está armado para tirar.
        doThrow(new PowerGridUnavailableException("caída", null))
                .when(powerGrid)
                .checkFeasibility(anyString(), any());
        ask(SERVICE_TOKEN, BODY)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("POWER_GRID_UNAVAILABLE"));
    }

    @Test
    @DisplayName("Un pedido sin nodo es 400, sin llegar a la distribuidora")
    void invalidBody() throws Exception {
        ask(SERVICE_TOKEN, "{\"requestedPowerKw\":150}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("Sin la clave de servicio, 401; con otra clave, 401")
    void requiresServiceToken() throws Exception {
        ask(null, BODY)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SERVICE_TOKEN"));
        ask("Bearer otra-clave", BODY).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El token válido de un conductor no abre la ruta interna")
    void driverTokenIsNotEnough() throws Exception {
        String driverToken = Jwts.builder()
                .subject("7")
                .claim("role", "CONDUCTOR")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        ask("Bearer " + driverToken, BODY).andExpect(status().isUnauthorized());
    }
}
