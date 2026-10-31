package com.ecopedia.core.pricing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.pricing.data.JpaPricingSchemeRepository;
import com.ecopedia.core.security.JwtTokenProvider;
import com.ecopedia.core.terminal.data.JpaConnectorRepository;
import com.ecopedia.core.terminal.data.JpaStationRepository;
import com.ecopedia.core.terminal.domain.Connector;
import com.ecopedia.core.terminal.domain.ConnectorType;
import com.ecopedia.core.terminal.domain.Station;
import com.ecopedia.core.terminal.domain.StationData;
import com.ecopedia.core.terminal.domain.TerminalService;
import com.ecopedia.core.user.data.JpaUserRepository;
import com.ecopedia.core.user.domain.RegistrationData;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La seña de un conector, por la ruta que consultan el checkout y la pantalla de reservar (ECO-53).
 *
 * <p>Existe por un defecto encontrado al probar en vivo: las dos la sacaban de {@code /estimate} con
 * un consumo de 10 kWh, y la estimación rechaza con 400 un consumo que no supera la seña. Con una seña
 * de $ 8.000 y una tarifa de $ 350 el kWh, la seña "no existía" y la reserva salía gratis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PricingDepositApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private TerminalService terminalService;

    @Autowired
    private UserService userService;

    @Autowired
    private JpaPricingSchemeRepository pricingSchemeRepository;

    @Autowired
    private JpaConnectorRepository connectorRepository;

    @Autowired
    private JpaStationRepository stationRepository;

    @Autowired
    private JpaUserRepository userRepository;

    @AfterEach
    void cleanUp() {
        pricingSchemeRepository.deleteAll();
        connectorRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Connector givenPricedConnector() throws Exception {
        User admin = userService.register(
                new RegistrationData("admin.sena@ecopedia.test", "unaClave123", "Admin", Role.ADMIN));
        Station station = terminalService.createStation(
                admin.getId(),
                new StationData("Estación con seña", "Av. San Juan 2901", -34.6037, -58.3816, List.of(), null));
        Connector connector = terminalService.addConnector(station.getId(), ConnectorType.CCS2, new BigDecimal("50"));

        mockMvc.perform(post("/api/pricing")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer "
                                        + tokenProvider.generateToken(admin.getId(), admin.getEmail(), admin.getRole()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"connectorId":%d,"strategyType":"FLAT_RATE","kwhRate":350,"depositAmount":8000}
                                """
                                        .formatted(connector.getId())))
                .andExpect(status().isCreated());
        return connector;
    }

    @Test
    @DisplayName("Devuelve la seña del conector, también una mayor que 10 kWh de tarifa, sin sesión")
    void returnsTheDepositEvenWhenItExceedsTheEstimate() throws Exception {
        Connector connector = givenPricedConnector();

        mockMvc.perform(get("/api/pricing/deposit").param("connectorId", String.valueOf(connector.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectorId").value(connector.getId()))
                .andExpect(jsonPath("$.depositAmount").value(8000.00));

        // El defecto: la estimación con 10 kWh rechaza esta misma tarifa.
        mockMvc.perform(get("/api/pricing/estimate")
                        .param("connectorId", String.valueOf(connector.getId()))
                        .param("estimatedKwh", "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Un conector sin esquema tarifario contesta 400: quien llama lo lee como sin seña")
    void connectorWithoutSchemeIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/pricing/deposit").param("connectorId", "999999"))
                .andExpect(status().isBadRequest());
    }
}
