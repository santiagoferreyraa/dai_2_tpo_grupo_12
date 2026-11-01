package com.ecopedia.core.pricing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.pricing.data.JpaStationPricingRepository;
import com.ecopedia.core.pricing.domain.PricingService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La seña de un conector, por la ruta que consultan el checkout y la pantalla de reservar (ECO-53): es la
 * de su estación desde el 31/10.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PricingDepositApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TerminalService terminalService;

    @Autowired
    private UserService userService;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private JpaStationPricingRepository stationPricingRepository;

    @Autowired
    private JpaConnectorRepository connectorRepository;

    @Autowired
    private JpaStationRepository stationRepository;

    @Autowired
    private JpaUserRepository userRepository;

    @AfterEach
    void cleanUp() {
        stationPricingRepository.deleteAll();
        connectorRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Connector givenConnector(String power) {
        User owner = userService.register(
                new RegistrationData("cpo.sena@ecopedia.test", "unaClave123", "Operador", Role.CPO));
        Station station = terminalService.createStation(
                owner.getId(),
                new StationData("Estación con seña", "Av. San Juan 2901", -34.6037, -58.3816, List.of(), null));
        return terminalService.addConnector(station.getId(), ConnectorType.CCS2, new BigDecimal(power));
    }

    @Test
    @DisplayName("Devuelve la seña de la estación del conector, sin sesión")
    void returnsTheStationsDeposit() throws Exception {
        Connector connector = givenConnector("150");
        pricingService.updateStationPricing(connector.getStation().getId(), new BigDecimal("8000"), true);

        mockMvc.perform(get("/api/pricing/deposit").param("connectorId", String.valueOf(connector.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectorId").value(connector.getId()))
                .andExpect(jsonPath("$.depositAmount").value(8000.00));
    }

    @Test
    @DisplayName("Una estación que nadie configuró no pide seña")
    void unconfiguredStationHasNoDeposit() throws Exception {
        Connector connector = givenConnector("22");

        mockMvc.perform(get("/api/pricing/deposit").param("connectorId", String.valueOf(connector.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositAmount").value(0));
    }

    @Test
    @DisplayName("La estimación dice el precio del kWh que se aplicaría y si es hora pico")
    void estimateTellsTheAppliedRate() throws Exception {
        Connector connector = givenConnector("150");

        // 12:00 en Buenos Aires: carga rápida a precio de red, $ 550 el kWh sembrado.
        mockMvc.perform(get("/api/pricing/estimate")
                        .param("connectorId", String.valueOf(connector.getId()))
                        .param("estimatedKwh", "10")
                        .param("start", "2026-11-02T15:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speed").value("FAST"))
                .andExpect(jsonPath("$.peak").value(false))
                .andExpect(jsonPath("$.kwhRate").value(550.00))
                .andExpect(jsonPath("$.estimatedCost").value(5500.00));

        // 19:00 en Buenos Aires: pico, $ 660 el kWh.
        mockMvc.perform(get("/api/pricing/estimate")
                        .param("connectorId", String.valueOf(connector.getId()))
                        .param("estimatedKwh", "10")
                        .param("start", "2026-11-02T22:00:00Z"))
                .andExpect(jsonPath("$.peak").value(true))
                .andExpect(jsonPath("$.kwhRate").value(660.00));
    }

    @Test
    @DisplayName("Un conector inexistente contesta 400: quien llama lo lee como sin seña")
    void unknownConnectorIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/pricing/deposit").param("connectorId", "999999"))
                .andExpect(status().isBadRequest());
    }
}
