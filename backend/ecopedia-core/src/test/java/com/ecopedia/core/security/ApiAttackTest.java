package com.ecopedia.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.terminal.data.JpaConnectorRepository;
import com.ecopedia.core.terminal.data.JpaStationRepository;
import com.ecopedia.core.terminal.domain.Station;
import com.ecopedia.core.terminal.domain.TerminalService;
import com.ecopedia.core.user.data.JpaUserRepository;
import com.ecopedia.core.user.domain.RegistrationData;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserService;
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
 * Pruebas de ataque sobre la API de core (QA-07, ECO-157): JWT hostil, mass assignment e
 * inyección SQL / XSS.
 *
 * <p><b>El rechazo hoy es 403, no 401.</b> Los criterios piden 401 ante un JWT vencido, alterado,
 * con {@code alg=none} o de otro emisor. Core contesta 403 a propósito (#40: el front detecta así
 * la sesión vencida). Está registrado como defecto (DEF-02, ver QA-33 / ECO-183). Cuando se
 * corrija, se cambia {@link #REJECTED_AS_ANONYMOUS} a 401 y estos tests pasan a exigir el código
 * nuevo: un solo lugar para tocar.
 *
 * <p><b>Cada grupo tiene un caso de control</b> con el pedido legítimo, que tiene que andar. Sin
 * él, un endpoint roto para todos haría pasar en verde los rechazos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApiAttackTest {

    /** Lo que recibe hoy un pedido sin sesión válida. Ver la nota de la clase y DEF-02. */
    private static final int REJECTED_AS_ANONYMOUS = 403;

    private static final String PASSWORD = "unaClave123";

    private static final String SQL_INJECTION = "Robert'); DROP TABLE users; --";

    private static final String XSS = "<script>alert('xss')</script>";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private TerminalService terminalService;

    @Autowired
    private JpaStationRepository stationRepository;

    @Autowired
    private JpaConnectorRepository connectorRepository;

    @Value("${ecopedia.jwt.secret}")
    private String secret;

    @AfterEach
    void cleanUp() {
        connectorRepository.deleteAll();
        stationRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User givenUser(String email, Role role) {
        return userService.register(new RegistrationData(email, PASSWORD, "Usuario " + role, role));
    }

    private String bearer(User user) {
        return HostileTokens.valid(secret, user.getId(), user.getRole().name());
    }

    /**
     * La estación creada por el test, buscada por nombre.
     *
     * <p>No la primera de la lista: el perfil {@code dev} siembra 15 estaciones, y según el orden
     * en que corran las clases todavía pueden estar ahí.
     */
    private Station stationNamed(String name) {
        return terminalService.getAllStations().stream()
                .filter(station -> name.equals(station.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se creó la estación " + name));
    }

    // --- JWT hostil -------------------------------------------------------------------------

    @Test
    @DisplayName("JWT · Control: con un token válido el perfil propio responde 200")
    void validTokenWorks() throws Exception {
        User driver = givenUser("jwt-ok@ecopedia.test", Role.CONDUCTOR);

        mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, bearer(driver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jwt-ok@ecopedia.test"));
    }

    @Test
    @DisplayName("JWT · Un token vencido no da acceso al perfil")
    void rejectsExpiredToken() throws Exception {
        User driver = givenUser("jwt-vencido@ecopedia.test", Role.CONDUCTOR);
        String token = HostileTokens.expired(secret, driver.getId(), "CONDUCTOR");

        mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    @Test
    @DisplayName("JWT · Un token con el sub cambiado al de un ADMIN, sin volver a firmar, no abre el backoffice")
    void rejectsTamperedToken() throws Exception {
        User driver = givenUser("jwt-alterado@ecopedia.test", Role.CONDUCTOR);
        User admin = givenUser("jwt-admin@ecopedia.test", Role.ADMIN);
        String forged = HostileTokens.tampered(
                bearer(driver), "\"sub\":\"" + driver.getId() + "\"", "\"sub\":\"" + admin.getId() + "\"");

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, forged))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    @Test
    @DisplayName("JWT · Un token con alg=none, sin firma, no da acceso")
    void rejectsAlgNone() throws Exception {
        User admin = givenUser("jwt-none@ecopedia.test", Role.ADMIN);

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.algNone(admin.getId(), "ADMIN")))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    @Test
    @DisplayName("JWT · Un token de otro emisor, firmado con su propia clave, no da acceso")
    void rejectsTokenFromAnotherIssuer() throws Exception {
        User admin = givenUser("jwt-ajeno@ecopedia.test", Role.ADMIN);

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, HostileTokens.fromAnotherIssuer(admin.getId(), "ADMIN")))
                .andExpect(status().is(REJECTED_AS_ANONYMOUS));
    }

    // --- Mass assignment --------------------------------------------------------------------

    @Test
    @DisplayName("Mass assignment · Editar el perfil con role, active, email e id de más: cambia el nombre y nada más")
    void profileIgnoresPrivilegedFields() throws Exception {
        User driver = givenUser("ma-perfil@ecopedia.test", Role.CONDUCTOR);

        mockMvc.perform(
                        put("/api/users/profile")
                                .header(HttpHeaders.AUTHORIZATION, bearer(driver))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"fullName":"Nombre Nuevo","role":"ADMIN","active":false,
                                 "email":"robado@ecopedia.test","id":1,"passwordHash":"x"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CONDUCTOR"));

        User after = userService.getProfile(driver.getId());
        assertThat(after.getFullName()).isEqualTo("Nombre Nuevo");
        assertThat(after.getRole()).isEqualTo(Role.CONDUCTOR);
        assertThat(after.isActive()).isTrue();
        assertThat(after.getEmail()).isEqualTo("ma-perfil@ecopedia.test");
        assertThat(after.getPasswordHash()).isNotEqualTo("x");
    }

    @Test
    @DisplayName("Mass assignment · Alta de estación con ownerId, active e id de más: el dueño es quien la crea")
    void stationIgnoresOwnerAndState() throws Exception {
        User operator = givenUser("ma-cpo@ecopedia.test", Role.CPO);

        mockMvc.perform(
                        post("/api/stations")
                                .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"name":"Estación MA","address":"Av. San Juan 2901",
                                 "latitude":-34.6,"longitude":-58.38,"photoUrls":[],
                                 "ownerId":999999,"active":false,"id":424242}
                                """))
                .andExpect(status().isCreated());

        Station created = stationNamed("Estación MA");
        assertThat(created.getOwnerId()).isEqualTo(operator.getId());
        assertThat(created.isActive()).isTrue();
        assertThat(created.getId()).isNotEqualTo(424242L);
    }

    // --- Inyección SQL y XSS ----------------------------------------------------------------

    @Test
    @DisplayName("Inyección · Un nombre con SQL y <script> se guarda como texto y vuelve como JSON, sin ejecutarse")
    void registrationStoresHostileTextAsData() throws Exception {
        String hostileName = SQL_INJECTION + " " + XSS;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"email":"inyeccion@ecopedia.test","password":"%s","fullName":"%s"}
                                """
                                        .formatted(PASSWORD, hostileName)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("$.fullName").value(hostileName))
                .andExpect(jsonPath("$.role").value("CONDUCTOR"));

        // La tabla sigue entera y el usuario entra: el DROP no se ejecutó.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"email":"inyeccion@ecopedia.test","password":"%s"}
                                """
                                        .formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @DisplayName("Inyección · Un login con ' OR '1'='1 en el email se rechaza con 400 y sin token")
    void loginRejectsSqlInEmail() throws Exception {
        givenUser("victima@ecopedia.test", Role.CONDUCTOR);

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"email":"' OR '1'='1' --","password":"' OR '1'='1' --"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @DisplayName("Inyección · La búsqueda con SQL en la latitud responde 400, no 500 ni resultados")
    void searchRejectsSqlInCoordinates() throws Exception {
        mockMvc.perform(get("/api/search").param("lat", "1 OR 1=1--").param("lon", "-58.38"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
            "Inyección · Estación con SQL en el nombre y HTML en la dirección: se guarda literal y el listado sigue")
    void stationStoresHostileTextAsData() throws Exception {
        User operator = givenUser("iny-cpo@ecopedia.test", Role.CPO);
        String hostileName = "'; DROP TABLE stations; --";
        String hostileAddress = "<img src=x onerror=alert(1)>";

        mockMvc.perform(post("/api/stations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"name":"%s","address":"%s","latitude":-34.6,"longitude":-58.38,"photoUrls":[]}
                                """
                                        .formatted(hostileName, hostileAddress)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/stations"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        Station stored = stationNamed(hostileName);
        assertThat(stored.getName()).isEqualTo(hostileName);
        assertThat(stored.getAddress()).isEqualTo(hostileAddress);
    }
}
