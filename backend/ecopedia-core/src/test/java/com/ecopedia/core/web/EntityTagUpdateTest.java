package com.ecopedia.core.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * El ETag de estaciones y perfil (QA-30, ECO-180): una actualización con un {@code If-Match} viejo
 * responde 412 y no pisa nada; con el vigente, guarda y devuelve el ETag nuevo; sin la cabecera,
 * guarda como antes (decisión de Manuel, 25/11).
 *
 * <p>Por la API entera, con Flyway, JPA y un token firmado de verdad: la versión la sube Hibernate al
 * hacer el commit, y eso solo se ve con la base levantada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EntityTagUpdateTest {

    private static final String OPERATOR_EMAIL = "cpo-etag@ecopedia.test";
    private static final String DRIVER_EMAIL = "conductor-etag@ecopedia.test";

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

    private String bearer(String email, Role role) {
        User user = userRepository
                .findByEmail(email)
                .orElseGet(() -> userService.register(new RegistrationData(email, "unaClave123", "Nombre", role)));
        return "Bearer " + tokenProvider.generateToken(user.getId(), email, role);
    }

    private String operator() {
        return bearer(OPERATOR_EMAIL, Role.CPO);
    }

    private String driver() {
        return bearer(DRIVER_EMAIL, Role.CONDUCTOR);
    }

    private static String station(String name) {
        return """
                {"name":"%s","address":"Av. San Juan 2901",
                 "latitude":-34.603754,"longitude":-58.381659,"photoUrls":[],"nodeId":null}
                """
                .formatted(name);
    }

    private long createStation() throws Exception {
        String body = mockMvc.perform(post("/api/stations")
                        .header(HttpHeaders.AUTHORIZATION, operator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(station("Original")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private String stationTag(long id) throws Exception {
        return mockMvc.perform(get("/api/stations/{id}", id))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);
    }

    private MvcResult putStation(long id, String ifMatch, String name) throws Exception {
        var request = put("/api/stations/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, operator())
                .contentType(MediaType.APPLICATION_JSON)
                .content(station(name));
        if (ifMatch != null) {
            request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return mockMvc.perform(request).andReturn();
    }

    private String nameOf(long stationId) throws Exception {
        String body = mockMvc.perform(get("/api/stations/{id}", stationId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("name").asText();
    }

    @Test
    @DisplayName("La estación trae su versión como ETag, la misma que viaja en el cuerpo")
    void stationExposesItsVersion() throws Exception {
        long id = createStation();

        MvcResult read = mockMvc.perform(get("/api/stations/{id}", id))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(read.getResponse().getContentAsString());
        assertThat(read.getResponse().getHeader(HttpHeaders.ETAG))
                .isEqualTo("\"" + body.get("version").asLong() + "\"");
        // El listado no lleva cabecera por estación, pero sí la versión de cada una.
        mockMvc.perform(get("/api/stations"))
                .andExpect(jsonPath("$[?(@.id == %d)].version".formatted(id))
                        .value(body.get("version").asInt()));
    }

    @Test
    @DisplayName("Con el ETag vigente se guarda y vuelve el ETag nuevo")
    void currentTagSaves() throws Exception {
        long id = createStation();
        String tag = stationTag(id);

        MvcResult saved = putStation(id, tag, "Cambiada");

        assertThat(saved.getResponse().getStatus()).isEqualTo(200);
        String newTag = saved.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(newTag).isNotNull().isNotEqualTo(tag);
        assertThat(newTag).isEqualTo(stationTag(id));
        JsonNode body = objectMapper.readTree(saved.getResponse().getContentAsString());
        assertThat("\"" + body.get("version").asLong() + "\"").isEqualTo(newTag);
        assertThat(nameOf(id)).isEqualTo("Cambiada");
    }

    @Test
    @DisplayName("Con un ETag viejo responde 412 STALE_VERSION y no pisa lo que guardó el otro")
    void staleTagIsRejected() throws Exception {
        long id = createStation();
        String tagReadByBoth = stationTag(id);
        assertThat(putStation(id, tagReadByBoth, "Del primero").getResponse().getStatus())
                .isEqualTo(200);

        mockMvc.perform(put("/api/stations/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, operator())
                        .header(HttpHeaders.IF_MATCH, tagReadByBoth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(station("Del segundo")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.code").value("STALE_VERSION"))
                .andExpect(jsonPath("$.status").value(412));

        assertThat(nameOf(id)).isEqualTo("Del primero");
    }

    @Test
    @DisplayName("Sin If-Match guarda como antes (decisión del 25/11)")
    void withoutTagSaves() throws Exception {
        long id = createStation();
        String before = stationTag(id);

        MvcResult saved = putStation(id, null, "Sin condición");

        assertThat(saved.getResponse().getStatus()).isEqualTo(200);
        assertThat(saved.getResponse().getHeader(HttpHeaders.ETAG)).isNotEqualTo(before);
        assertThat(nameOf(id)).isEqualTo("Sin condición");
    }

    @Test
    @DisplayName("If-Match * guarda; una etiqueta débil o ajena no coincide con nada y da 412")
    void ifMatchForms() throws Exception {
        long id = createStation();
        String tag = stationTag(id);
        String version = tag.substring(1, tag.length() - 1);

        assertThat(putStation(id, "W/" + tag, "Débil").getResponse().getStatus())
                .isEqualTo(412);
        assertThat(putStation(id, "cualquier-cosa", "Ajena").getResponse().getStatus())
                .isEqualTo(412);
        assertThat(putStation(id, version, "Sin comillas").getResponse().getStatus())
                .isEqualTo(412);
        assertThat(nameOf(id)).isEqualTo("Original");

        // Una lista sirve si alguna coincide.
        assertThat(putStation(id, "\"999\", " + tag, "De la lista")
                        .getResponse()
                        .getStatus())
                .isEqualTo(200);
        assertThat(putStation(id, "*", "Con asterisco").getResponse().getStatus())
                .isEqualTo(200);
        assertThat(nameOf(id)).isEqualTo("Con asterisco");
    }

    @Test
    @DisplayName("Una estación inexistente sigue dando el 404 único, también con If-Match")
    void missingStationIsStillNotFound() throws Exception {
        mockMvc.perform(put("/api/stations/{id}", 987654)
                        .header(HttpHeaders.AUTHORIZATION, bearer("admin-etag@ecopedia.test", Role.ADMIN))
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(station("Nada")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("El perfil: ETag al leer, 412 con uno viejo sin tocar el nombre, 200 con el vigente")
    void profile() throws Exception {
        String token = driver();
        MvcResult read = mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andReturn();
        String tag = read.getResponse().getHeader(HttpHeaders.ETAG);
        JsonNode body = objectMapper.readTree(read.getResponse().getContentAsString());
        assertThat(tag).isEqualTo("\"" + body.get("version").asLong() + "\"");

        MvcResult first = mockMvc.perform(put("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaders.IF_MATCH, tag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Primera pestaña\",\"vehicleModelId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Primera pestaña"))
                .andReturn();
        String newTag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(newTag).isNotEqualTo(tag);

        mockMvc.perform(put("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaders.IF_MATCH, tag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Segunda pestaña\",\"vehicleModelId\":null}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));

        mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.fullName").value("Primera pestaña"))
                .andExpect(header().string(HttpHeaders.ETAG, newTag));

        mockMvc.perform(put("/api/users/profile")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaders.IF_MATCH, newTag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Segunda pestaña\",\"vehicleModelId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Segunda pestaña"));
    }
}
