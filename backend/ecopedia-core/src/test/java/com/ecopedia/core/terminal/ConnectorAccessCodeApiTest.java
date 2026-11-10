package com.ecopedia.core.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.security.JwtTokenProvider;
import com.ecopedia.core.terminal.data.JpaConnectorRepository;
import com.ecopedia.core.terminal.data.JpaStationRepository;
import com.ecopedia.core.user.data.JpaUserRepository;
import com.ecopedia.core.user.domain.RegistrationData;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El código de acceso de los conectores por la API entera (RF11, ECO-44): quién lo ve, quién lo
 * resuelve y que no se filtre por la consulta pública del conector.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ConnectorAccessCodeApiTest {

    private static final String RESOLVE = "/internal/terminals/connectors/resolve-code";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserService userService;

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private JpaStationRepository stationRepository;

    @Autowired
    private JpaConnectorRepository connectorRepository;

    @Value("${ecopedia.internal.token}")
    private String serviceToken;

    /*
     * Solo se borra lo que creó esta clase: las estaciones del seed las necesitan otras pruebas que
     * comparten la misma base (InitialSetupTest cuenta las 15).
     */
    private final List<Long> createdStations = new ArrayList<>();
    private final List<String> createdEmails = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long stationId : createdStations) {
            connectorRepository.deleteAll(connectorRepository.findByStationId(stationId));
            stationRepository.deleteById(stationId);
        }
        createdStations.clear();
        createdEmails.forEach(
                email -> userRepository.findByEmail(email).ifPresent(user -> userRepository.deleteById(user.getId())));
        createdEmails.clear();
    }

    private String bearerFor(String email, Role role) {
        if (!createdEmails.contains(email)) {
            createdEmails.add(email);
        }
        User user = userRepository
                .findByEmail(email)
                .orElseGet(() -> userService.register(new RegistrationData(email, "unaClave123", "Usuario", role)));
        return "Bearer " + tokenProvider.generateToken(user.getId(), email, role);
    }

    private String owner() {
        return bearerFor("cpo@ecopedia.test", Role.CPO);
    }

    private long createConnector() throws Exception {
        String station = mockMvc.perform(
                        post("/api/stations")
                                .header(HttpHeaders.AUTHORIZATION, owner())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"name":"Estación con PIN","address":"Av. San Juan 2901",
                                 "latitude":-34.603754,"longitude":-58.381659,"photoUrls":[]}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long stationId = objectMapper.readTree(station).get("id").asLong();
        createdStations.add(stationId);

        String connector = mockMvc.perform(post("/api/stations/{id}/connectors", stationId)
                        .header(HttpHeaders.AUTHORIZATION, owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorType\":\"CCS2\",\"maxPowerKw\":150}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessCode").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(connector).get("id").asLong();
    }

    private JsonNode accessCodeOf(long connectorId) throws Exception {
        String body = mockMvc.perform(
                        get("/api/connectors/{id}/access-code", connectorId).header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private static String resolveBody(String code) {
        return "{\"code\":\"" + code + "\"}";
    }

    @Test
    @DisplayName("El operador dueño ve el PIN y el QR; la consulta pública del conector no lo muestra")
    void ownerSeesCodePublicDoesNot() throws Exception {
        long connectorId = createConnector();

        JsonNode code = accessCodeOf(connectorId);
        assertThat(code.get("accessCode").asText()).matches("\\d{6}");
        assertThat(code.get("qrPayload").asText())
                .isEqualTo("ecopedia:connector:" + code.get("accessCode").asText());

        mockMvc.perform(get("/api/connectors/{id}", connectorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessCode").doesNotExist());
    }

    @Test
    @DisplayName("Otro operador y un conductor no ven el código: el mismo 404 que un conector que no existe")
    void othersCannotSeeCode() throws Exception {
        long connectorId = createConnector();

        mockMvc.perform(get("/api/connectors/{id}/access-code", connectorId)
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("otro-cpo@ecopedia.test", Role.CPO)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/connectors/{id}/access-code", connectorId)
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("conductor@ecopedia.test", Role.CONDUCTOR)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Con la clave de servicio, el PIN y el QR resuelven al conector")
    void serviceResolvesPinAndQr() throws Exception {
        long connectorId = createConnector();
        String pin = accessCodeOf(connectorId).get("accessCode").asText();

        mockMvc.perform(post(RESOLVE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveBody(pin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(connectorId));

        mockMvc.perform(post(RESOLVE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveBody("ecopedia:connector:" + pin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(connectorId));
    }

    @Test
    @DisplayName("Sin la clave de servicio no se resuelve nada, ni con el token de un conductor")
    void resolveNeedsServiceToken() throws Exception {
        long connectorId = createConnector();
        String pin = accessCodeOf(connectorId).get("accessCode").asText();

        mockMvc.perform(post(RESOLVE).contentType(MediaType.APPLICATION_JSON).content(resolveBody(pin)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(RESOLVE)
                        .header(HttpHeaders.AUTHORIZATION, bearerFor("conductor@ecopedia.test", Role.CONDUCTOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveBody(pin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Al regenerar, el código viejo deja de abrir y el nuevo abre")
    void regenerateInvalidatesOldCode() throws Exception {
        long connectorId = createConnector();
        String oldPin = accessCodeOf(connectorId).get("accessCode").asText();

        String body = mockMvc.perform(post("/api/connectors/{id}/access-code", connectorId)
                        .header(HttpHeaders.AUTHORIZATION, owner()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String newPin = objectMapper.readTree(body).get("accessCode").asText();

        if (!newPin.equals(oldPin)) {
            mockMvc.perform(post(RESOLVE)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resolveBody(oldPin)))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(post(RESOLVE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolveBody(newPin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(connectorId));
    }
}
