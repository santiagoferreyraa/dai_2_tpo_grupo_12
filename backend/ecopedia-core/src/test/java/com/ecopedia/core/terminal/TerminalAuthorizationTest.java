package com.ecopedia.core.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.util.stream.Stream;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Seguridad declarativa del ABM de estaciones (ECO-25).
 *
 * <p><b>Qué prueba y por qué existe.</b> El criterio de ECO-25 pide que el ABM de estaciones
 * solo lo pueda hacer un Operador, <i>verificado con un test</i>. Hasta acá no había ninguno:
 * {@code mvn verify} pasaba en verde con los siete endpoints abiertos de par en par, porque
 * ningún test afirmaba que un endpoint estuviera protegido. Un build verde no prueba que algo
 * esté cerrado; solo prueba que nadie preguntó.
 *
 * <p><b>Usa un JWT de verdad, no un contexto de seguridad simulado.</b> Se firma con
 * {@link JwtTokenProvider} y viaja en la cabecera {@code Authorization}, así que cada prueba
 * recorre la cadena entera —el filtro extrae el token, arma la autoridad {@code ROLE_<rol>} y
 * recién ahí {@code @PreAuthorize} decide—. Con {@code @WithMockUser} se saltearía el filtro,
 * que es justo la pieza que puede romperse en silencio; además evita sumar
 * {@code spring-security-test} al POM a una semana de la entrega.
 *
 * <p><b>El anónimo recibe 403 y no 401</b>, porque la cadena de {@code SecurityConfig} deja
 * pasar todas las URL ({@code anyRequest().permitAll()}) y quien rechaza es la anotación sobre
 * el método, ya dentro del controlador. Es correcto, y conviene saberlo antes de la demo: acá
 * el 403 significa "no autenticado", no "autenticado sin permiso".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TerminalAuthorizationTest {

    private static final String STATION_JSON =
            """
            {"name":"Estación de prueba","address":"Av. San Juan 2901",
             "latitude":-34.603754,"longitude":-58.381659,"photoUrls":[]}
            """;

    private static final String CONNECTOR_JSON =
            """
            {"connectorType":"CCS2","maxPowerKw":50}
            """;

    private static final String STATUS_JSON = """
            {"operationalStatus":"OUT_OF_SERVICE"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private TerminalService terminalService;

    @Autowired
    private JpaStationRepository stationRepository;

    @Autowired
    private JpaConnectorRepository connectorRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JpaUserRepository userRepository;

    @AfterEach
    void cleanUp() {
        connectorRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
    }

    /** Token firmado de verdad para el rol pedido, tal como lo emite el login. */
    private String bearer(Role role) {
        return bearerFor(role.name().toLowerCase() + "@ecopedia.test", role);
    }

    /**
     * Token de un usuario que existe de verdad en la base.
     *
     * <p>Tiene que existir: el dueño de una estación se resuelve por el usuario del token, y un
     * token de alguien que no está en la base no es dueño de nada.
     */
    private String bearerFor(String email, Role role) {
        User user = userRepository
                .findByEmail(email)
                .orElseGet(() ->
                        userService.register(new RegistrationData(email, "unaClave123", "Usuario " + role, role)));
        return "Bearer " + tokenProvider.generateToken(user.getId(), email, role);
    }

    /**
     * Las seis operaciones de ABM que son **exclusivas del operador**: la baja de estación
     * queda afuera porque el backoffice también la puede ejecutar (RF03), y por eso se prueba
     * aparte en {@link #allowsStationDeactivationForAdmins()}.
     *
     * <p>Los ids no existen a propósito: si la autorización rechaza, nunca se llega al cuerpo
     * del método, así que el id da igual. Ese es justamente el punto — {@code @PreAuthorize}
     * corta antes de entrar.
     */
    private List<MockHttpServletRequestBuilder> operatorOnlyRequests() {
        return List.of(
                post("/api/stations").contentType(MediaType.APPLICATION_JSON).content(STATION_JSON),
                put("/api/stations/1").contentType(MediaType.APPLICATION_JSON).content(STATION_JSON),
                post("/api/stations/1/connectors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONNECTOR_JSON),
                post("/api/connectors/1/configure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONNECTOR_JSON),
                patch("/api/connectors/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATUS_JSON),
                delete("/api/connectors/1"));
    }

    /** Las siete: las seis del operador más la baja de estación. */
    private List<MockHttpServletRequestBuilder> abmRequests() {
        return Stream.concat(operatorOnlyRequests().stream(), Stream.of(delete("/api/stations/1")))
                .toList();
    }

    @Test
    @DisplayName("Sin token, las siete operaciones de ABM son rechazadas")
    void rejectsTheWholeAbmForAnonymousCallers() throws Exception {
        for (MockHttpServletRequestBuilder request : abmRequests()) {
            mockMvc.perform(request).andExpect(status().isForbidden());
        }
    }

    /*
     * El caso que más importa de los tres: no alcanza con exigir un token, hay que exigir el
     * rol correcto. Un conductor autenticado es exactamente quien podría dar de baja la
     * estación ajena si la regla fuera "estar logueado".
     */
    @Test
    @DisplayName("Con token de CONDUCTOR, las siete operaciones de ABM son rechazadas")
    void rejectsTheWholeAbmForDrivers() throws Exception {
        for (MockHttpServletRequestBuilder request : abmRequests()) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(Role.CONDUCTOR)))
                    .andExpect(status().isForbidden());
        }
    }

    /*
     * El ADMIN puede ejecutar el ABM completo desde el Backoffice (ECO-27).
     */
    @Test
    @DisplayName("Con token de ADMIN, el ABM completo también está permitido")
    void allowsTheWholeAbmForAdmins() throws Exception {
        String admin = bearer(Role.ADMIN);

        mockMvc.perform(post("/api/stations")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATION_JSON))
                .andExpect(status().isCreated());

        Station station = terminalService.getAllStations().get(0);

        mockMvc.perform(post("/api/stations/" + station.getId() + "/connectors")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONNECTOR_JSON))
                .andExpect(status().isCreated());

        Connector connector = connectorRepository.findAll().get(0);

        mockMvc.perform(post("/api/connectors/" + connector.getId() + "/configure")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONNECTOR_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/connectors/" + connector.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATUS_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/stations/" + station.getId())
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/connectors/" + connector.getId()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/stations/" + station.getId()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Con token de CPO, el ABM completo funciona de punta a punta")
    void allowsTheWholeAbmForOperators() throws Exception {
        String cpo = bearer(Role.CPO);

        mockMvc.perform(post("/api/stations")
                        .header(HttpHeaders.AUTHORIZATION, cpo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATION_JSON))
                .andExpect(status().isCreated());

        Station station = terminalService.getAllStations().get(0);

        mockMvc.perform(post("/api/stations/" + station.getId() + "/connectors")
                        .header(HttpHeaders.AUTHORIZATION, cpo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONNECTOR_JSON))
                .andExpect(status().isCreated());

        Connector connector = connectorRepository.findAll().get(0);

        mockMvc.perform(post("/api/connectors/" + connector.getId() + "/configure")
                        .header(HttpHeaders.AUTHORIZATION, cpo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONNECTOR_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/connectors/" + connector.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, cpo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATUS_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/stations/" + station.getId())
                        .header(HttpHeaders.AUTHORIZATION, cpo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/connectors/" + connector.getId()).header(HttpHeaders.AUTHORIZATION, cpo))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/stations/" + station.getId()).header(HttpHeaders.AUTHORIZATION, cpo))
                .andExpect(status().isNoContent());
    }

    /*
     * El agujero que tapa TerminalAccess: con el rol solo, cualquier CPO editaba, apagaba o
     * borraba la estación de otro. El dueño la crea por la API; el otro operador prueba las seis
     * operaciones sobre lo ajeno y todas rebotan, y al final la estación y el conector siguen
     * intactos.
     */
    @Test
    @DisplayName("Un CPO no puede tocar la estación ni los conectores de otro CPO")
    void rejectsOperatorsOnStationsTheyDoNotOwn() throws Exception {
        String owner = bearerFor("duenio@ecopedia.test", Role.CPO);
        String other = bearerFor("otro@ecopedia.test", Role.CPO);

        mockMvc.perform(post("/api/stations")
                        .header(HttpHeaders.AUTHORIZATION, owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATION_JSON))
                .andExpect(status().isCreated());
        Station station = terminalService.getAllStations().get(0);
        Connector connector = terminalService.addConnector(station.getId(), ConnectorType.CCS2, new BigDecimal("50"));

        List<MockHttpServletRequestBuilder> onForeignStation = List.of(
                put("/api/stations/" + station.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATION_JSON.replace("Estación de prueba", "Cambiada por otro")),
                post("/api/stations/" + station.getId() + "/connectors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONNECTOR_JSON),
                post("/api/connectors/" + connector.getId() + "/configure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CONNECTOR_JSON),
                patch("/api/connectors/" + connector.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATUS_JSON),
                delete("/api/connectors/" + connector.getId()),
                delete("/api/stations/" + station.getId()));

        for (MockHttpServletRequestBuilder request : onForeignStation) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, other)).andExpect(status().isForbidden());
        }

        Station after = terminalService.getStation(station.getId());
        assertThat(after.getName()).isEqualTo("Estación de prueba");
        assertThat(after.isActive()).isTrue();
        assertThat(terminalService.getConnectorsByStation(station.getId())).hasSize(1);
    }

    @Test
    @DisplayName("El alta deja como dueño al operador que la hace, no a un id fijo")
    void recordsTheCreatorAsOwner() throws Exception {
        bearerFor("relleno@ecopedia.test", Role.CONDUCTOR);
        String operator = bearerFor("operador@ecopedia.test", Role.CPO);
        Long operatorId = userRepository
                .findByEmail("operador@ecopedia.test")
                .orElseThrow()
                .getId();

        mockMvc.perform(post("/api/stations")
                        .header(HttpHeaders.AUTHORIZATION, operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATION_JSON))
                .andExpect(status().isCreated());

        assertThat(terminalService.getAllStations().get(0).getOwnerId()).isEqualTo(operatorId);
    }

    /*
     * La contracara: cerrar el ABM no puede cerrarle la búsqueda al conductor, que según §2.3
     * es pública y es RF07. Si esta prueba se pone en rojo, la pantalla del mapa dejó de
     * funcionar para todo el mundo.
     */
    @Test
    @DisplayName("La búsqueda y la lectura siguen abiertas sin token")
    void keepsSearchAndReadsOpen() throws Exception {
        Station station = terminalService.createStation(
                1L, new StationData("Estación pública", "Av. San Juan 2901", -34.603754, -58.381659, List.of()));
        terminalService.addConnector(station.getId(), ConnectorType.CCS2, new BigDecimal("50"));
        Connector connector = connectorRepository.findAll().get(0);

        mockMvc.perform(get("/api/search").param("lat", "-34.6").param("lon", "-58.38"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/stations")).andExpect(status().isOk());
        mockMvc.perform(get("/api/stations/" + station.getId())).andExpect(status().isOk());
        mockMvc.perform(get("/api/connectors/" + connector.getId())).andExpect(status().isOk());
    }
}
