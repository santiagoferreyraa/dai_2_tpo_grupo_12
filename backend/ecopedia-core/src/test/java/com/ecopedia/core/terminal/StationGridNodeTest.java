package com.ecopedia.core.terminal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * El nodo de la distribuidora de cada estación (RF15): se carga en el alta, se cambia y se borra
 * en la edición, y solo se acepta con el formato de los códigos de la distribuidora.
 *
 * <p>Pasa por la API entera, con un token de operador firmado de verdad, por lo mismo que
 * {@code TerminalAuthorizationTest}: lo que importa es que el campo viaje del JSON a la base y
 * vuelva, y eso solo se ve con Flyway, JPA y la capa web levantados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class StationGridNodeTest {

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

    @AfterEach
    void cleanUp() {
        connectorRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String operator() {
        String email = "cpo@ecopedia.test";
        User user = userRepository
                .findByEmail(email)
                .orElseGet(
                        () -> userService.register(new RegistrationData(email, "unaClave123", "Operador", Role.CPO)));
        return "Bearer " + tokenProvider.generateToken(user.getId(), email, Role.CPO);
    }

    private static String station(String nodeJson) {
        return """
                {"name":"Estación con nodo","address":"Av. San Juan 2901",
                 "latitude":-34.603754,"longitude":-58.381659,"photoUrls":[],"nodeId":%s}
                """
                .formatted(nodeJson);
    }

    private long create(String nodeJson) throws Exception {
        String body = mockMvc.perform(post("/api/stations")
                        .header(HttpHeaders.AUTHORIZATION, operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(station(nodeJson)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode created = objectMapper.readTree(body);
        return created.get("id").asLong();
    }

    @Test
    @DisplayName("El alta guarda el nodo y la estación lo devuelve")
    void createsWithNode() throws Exception {
        long id = create("\"CABA-CENTRO\"");

        mockMvc.perform(get("/api/stations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value("CABA-CENTRO"));
    }

    @Test
    @DisplayName("El alta sin nodo, o con el campo vacío, deja la estación sin nodo")
    void createsWithoutNode() throws Exception {
        long withNull = create("null");
        long withBlank = create("\"\"");

        mockMvc.perform(get("/api/stations/{id}", withNull))
                .andExpect(jsonPath("$.nodeId").doesNotExist());
        mockMvc.perform(get("/api/stations/{id}", withBlank))
                .andExpect(jsonPath("$.nodeId").doesNotExist());
    }

    @Test
    @DisplayName("La edición cambia el nodo, y sin nodo lo borra")
    void updatesAndClearsNode() throws Exception {
        long id = create("\"CABA-CENTRO\"");

        mockMvc.perform(put("/api/stations/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(station("\"GBA-NORTE\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").value("GBA-NORTE"));

        mockMvc.perform(put("/api/stations/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(station("\"\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeId").doesNotExist());
    }

    /*
     * Minúsculas, espacios, guiones en los bordes y textos largos: nada de eso es un código de la
     * distribuidora, y lo que se guarda acá es lo que después viaja en el pedido SOAP.
     */
    @Test
    @DisplayName("Un nodo con otro formato se rechaza con 400")
    void rejectsMalformedNode() throws Exception {
        for (String node : new String[] {
            "\"caba-centro\"", "\"CABA CENTRO\"", "\"-CABA\"", "\"CABA-\"", "\"<x/>\"", "\"" + "A".repeat(41) + "\""
        }) {
            mockMvc.perform(post("/api/stations")
                            .header(HttpHeaders.AUTHORIZATION, operator())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(station(node)))
                    .andExpect(status().isBadRequest());
        }
    }
}
