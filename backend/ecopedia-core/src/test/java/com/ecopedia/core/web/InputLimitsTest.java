package com.ecopedia.core.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.security.JwtTokenProvider;
import com.ecopedia.core.terminal.domain.Station;
import com.ecopedia.core.terminal.domain.StationData;
import com.ecopedia.core.terminal.domain.TerminalService;
import com.ecopedia.core.user.domain.RegistrationData;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los topes de los números que manda el ABM (QA-25). Schemathesis encontró que una potencia o una
 * seña más grandes que la columna daban 500 al guardar, y que una estación se creaba con cualquier
 * latitud. Ahora cada uno da 400 con el motivo, y el valor límite se sigue aceptando.
 *
 * <p>{@code @Transactional}: cada prueba se deshace al terminar y no deja estaciones ni usuarios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class InputLimitsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserService userService;

    @Autowired
    private TerminalService terminalService;

    private String admin;
    private Station station;

    @BeforeEach
    void setUp() {
        User user = userService.register(
                new RegistrationData("admin-topes@ecopedia.test", "unaClave123", "Admin topes", Role.ADMIN));
        admin = "Bearer " + tokenProvider.generateToken(user.getId(), user.getEmail(), Role.ADMIN);
        station = terminalService.createStation(
                user.getId(), new StationData("Topes", "Av. Corrientes 1", -34.6, -58.4, null, null));
    }

    private ResultActions send(String method, String path, String json) throws Exception {
        var request = "PUT".equals(method) ? put(path) : post(path);
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String station(String latitude, String longitude) {
        return """
                {"name": "Borde", "address": "Calle 1", "latitude": %s, "longitude": %s}"""
                .formatted(latitude, longitude);
    }

    @Test
    @DisplayName("La latitud y la longitud tienen que existir: 90 y 180 sí, 90,1 y 180,1 no")
    void coordinates() throws Exception {
        send("POST", "/api/stations", station("90", "180")).andExpect(status().isCreated());
        send("POST", "/api/stations", station("-90", "-180")).andExpect(status().isCreated());

        send("POST", "/api/stations", station("90.1", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La latitud va de -90 a 90"));
        send("POST", "/api/stations", station("0", "-180.1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La longitud va de -180 a 180"));
        send("POST", "/api/stations", station("-24028726891444052", "0")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("La potencia de un conector llega hasta 9999,99 kW, al crearlo y al configurarlo")
    void connectorPower() throws Exception {
        String path = "/api/stations/" + station.getId() + "/connectors";
        String created = send(
                        "POST", path, """
                        {"connectorType": "CCS2", "maxPowerKw": 9999.99}""")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        send("POST", path, """
                        {"connectorType": "CCS2", "maxPowerKw": 9223372036854775808}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La potencia máxima no puede superar 9999,99 kW"));

        String id = created.replaceAll(".*\"id\":(\\d+).*", "$1");
        send(
                        "POST",
                        "/api/connectors/" + id + "/configure",
                        """
                        {"connectorType": "CCS2", "maxPowerKw": 10000}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Los textos llegan hasta el largo de su columna: 120 sí, 121 no")
    void textLengths() throws Exception {
        String max = "a".repeat(120);
        String over = "a".repeat(121);
        send(
                        "POST",
                        "/api/stations",
                        """
                        {"name": "%s", "address": "Calle 1", "latitude": 0, "longitude": 0}"""
                                .formatted(max))
                .andExpect(status().isCreated());
        send(
                        "POST",
                        "/api/stations",
                        """
                        {"name": "%s", "address": "Calle 1", "latitude": 0, "longitude": 0}"""
                                .formatted(over))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El nombre no puede superar los 120 caracteres"));
        send(
                        "PUT",
                        "/api/stations/" + station.getId(),
                        """
                        {"name": "Topes", "address": "%s", "latitude": 0, "longitude": 0}"""
                                .formatted("a".repeat(256)))
                .andExpect(status().isBadRequest());
        send(
                        "POST",
                        "/api/auth/register",
                        """
                        {"email": "largo@ecopedia.test", "password": "unaClave123", "fullName": "%s"}"""
                                .formatted(over))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El nombre completo no puede superar los 120 caracteres"));
        send(
                        "POST",
                        "/api/auth/register",
                        """
                        {"email": "%s@ecopedia.test", "password": "unaClave123", "fullName": "Largo"}"""
                                .formatted(max))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Una foto en null o vacía no se guarda")
    void blankPhotos() throws Exception {
        send(
                        "POST",
                        "/api/stations",
                        """
                        {"name": "Fotos", "address": "Calle 1", "latitude": 0, "longitude": 0, "photoUrls": [null, ""]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La dirección de una foto no puede estar vacía"));
    }

    @Test
    @DisplayName("El estimado pide kWh mayores a cero y hasta 10000; el costo real, nada negativo")
    void estimateAndRealCost() throws Exception {
        for (String kwh : new String[] {"-1.7976931348623157e308", "0", "10000.01"}) {
            mockMvc.perform(get("/api/pricing/estimate")
                            .param("connectorId", "1")
                            .param("estimatedKwh", kwh))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/pricing/real-cost")
                        .param("connectorId", "1")
                        .param("kwhConsumed", "-1")
                        .param("deliveredPowerKw", "50")
                        .param("start", "2026-11-25T12:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Los kWh consumidos no pueden ser negativos"));
    }

    @Test
    @DisplayName("La seña de una estación llega hasta $ 99.999.999,99")
    void deposit() throws Exception {
        String path = "/api/pricing/stations/" + station.getId();
        send("PUT", path, """
                        {"depositAmount": 99999999.99, "peakPricingEnabled": false}""")
                .andExpect(status().isOk());
        send("PUT", path, """
                        {"depositAmount": 100000000, "peakPricingEnabled": false}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La seña no puede superar $ 99.999.999,99"));
    }

    @Test
    @DisplayName("Los precios de la red llegan hasta $ 99.999.999,99")
    void networkRates() throws Exception {
        send(
                        "PUT",
                        "/api/pricing/rates",
                        """
                        {"normalKwhRate": 100000000, "fastKwhRate": 1, "excessPenaltyPerMin": 0}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El precio de la carga normal no puede superar $ 99.999.999,99"));
        send(
                        "PUT",
                        "/api/pricing/rates",
                        """
                        {"normalKwhRate": 1, "fastKwhRate": 1, "excessPenaltyPerMin": 100000000}""")
                .andExpect(status().isBadRequest());
        send(
                        "PUT",
                        "/api/pricing/rates",
                        """
                        {"normalKwhRate": 99999999.99, "fastKwhRate": 99999999.99, "excessPenaltyPerMin": 99999999.99}""")
                .andExpect(status().isOk());
    }
}
