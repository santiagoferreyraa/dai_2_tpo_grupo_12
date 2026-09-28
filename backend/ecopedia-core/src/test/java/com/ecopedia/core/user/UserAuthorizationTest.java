package com.ecopedia.core.user;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.security.JwtTokenProvider;
import com.ecopedia.core.user.data.JpaUserRepository;
import com.ecopedia.core.user.domain.Credentials;
import com.ecopedia.core.user.domain.RegistrationData;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Seguridad declarativa del backoffice de usuarios (ECO-25).
 *
 * <p><b>La otra mitad del criterio.</b> ECO-25 pide que el ABM de estaciones sea solo del
 * Operador <i>y que el backoffice sea solo del Administrador</i>, las dos cosas verificadas
 * con un test. El ABM lo cubre {@code TerminalAuthorizationTest}; esta clase cubre el
 * backoffice, que tenía las anotaciones puestas y ninguna prueba que las sostuviera. Sin esto,
 * borrar un {@code @PreAuthorize} en un merge deja {@code mvn verify} en verde.
 *
 * <p><b>Firma un JWT de verdad</b>, igual que la clase hermana, así que cada caso recorre el
 * filtro entero antes de llegar a la anotación. Para el test no hace falta promover un usuario
 * a administrador en la base: el token se emite directamente con el rol pedido, que es lo mismo
 * que devolvería el login de un usuario ya promovido.
 *
 * <p><b>El perfil propio no es backoffice y por eso se prueba aparte.</b> {@code /profile}
 * responde con los datos de quien pregunta, sea cual sea su rol; lo único que exige es estar
 * autenticado. Meterlo en la lista de operaciones restringidas sería documentar una regla que
 * el dominio no tiene.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class UserAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserService userService;

    @Autowired
    private JpaUserRepository userRepository;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    /**
     * Token firmado de verdad para el rol pedido, de un usuario que existe en la base: el filtro
     * busca al usuario del token y, si no está, la petición sigue como anónima.
     */
    private String bearer(Role role) {
        String email = role.name().toLowerCase() + "@ecopedia.test";
        User user = userRepository.findByEmail(email).orElseGet(() -> givenUser(email, role));
        return "Bearer " + tokenProvider.generateToken(user.getId(), email, role);
    }

    private User givenUser(String email, Role role) {
        return userService.register(new RegistrationData(email, "unaClave123", "Usuario " + role, role));
    }

    /** Las tres operaciones de backoffice que declara {@code UserController}. */
    private List<MockHttpServletRequestBuilder> backOfficeRequests() {
        return List.of(get("/api/users"), get("/api/users/1"), delete("/api/users/1"));
    }

    @Test
    @DisplayName("Sin token, las tres operaciones de backoffice son rechazadas")
    void rejectsBackOfficeForAnonymousCallers() throws Exception {
        for (MockHttpServletRequestBuilder request : backOfficeRequests()) {
            mockMvc.perform(request).andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Con token de CONDUCTOR, las tres operaciones de backoffice son rechazadas")
    void rejectsBackOfficeForDrivers() throws Exception {
        for (MockHttpServletRequestBuilder request : backOfficeRequests()) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(Role.CONDUCTOR)))
                    .andExpect(status().isForbidden());
        }
    }

    /*
     * El operador es el caso que un chequeo por "estar logueado" dejaría pasar y el que más
     * daño hace: es un usuario legítimo de la plataforma, con rol propio, que no tiene por qué
     * ver el padrón de conductores ni darlos de baja.
     */
    @Test
    @DisplayName("Con token de CPO, las tres operaciones de backoffice son rechazadas")
    void rejectsBackOfficeForOperators() throws Exception {
        for (MockHttpServletRequestBuilder request : backOfficeRequests()) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(Role.CPO)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Con token de ADMIN, el backoffice funciona: listar, consultar y dar de baja")
    void allowsBackOfficeForAdmins() throws Exception {
        String admin = bearer(Role.ADMIN);
        User driver = givenUser("conductor@ecopedia.test", Role.CONDUCTOR);

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                // El ADMIN que llama también está en la base, así que el conductor no es el único.
                .andExpect(jsonPath("$[*].email", hasItem("conductor@ecopedia.test")));

        mockMvc.perform(get("/api/users/" + driver.getId()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CONDUCTOR"));

        mockMvc.perform(delete("/api/users/" + driver.getId()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());

        /*
         * La baja es lógica: el usuario sigue existiendo y la consulta lo devuelve con active
         * en false. Comprobarlo importa porque un 204 solo dice que la operación se aceptó, no
         * que haya hecho lo que promete.
         */
        mockMvc.perform(get("/api/users/" + driver.getId()).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    /*
     * El filtro por rol es la única variante de la operación de listado, y se prueba acá
     * porque es la consulta con la que el administrador arma el padrón de operadores.
     */
    @Test
    @DisplayName("El ADMIN puede filtrar el listado por rol")
    void allowsFilteringUsersByRole() throws Exception {
        givenUser("conductor@ecopedia.test", Role.CONDUCTOR);
        givenUser("operador@ecopedia.test", Role.CPO);

        mockMvc.perform(get("/api/users").param("role", "CPO").header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("operador@ecopedia.test"));
    }

    /*
     * La prueba que sostiene el cierre del escalamiento de privilegios. El registro es público
     * —tiene que serlo, es el alta de conductores—, así que si aceptara el rol por parámetro
     * cualquiera se emitiría una cuenta de administrador y las diez anotaciones del sistema
     * dejarían de valer: el atacante no las esquiva, llega con el rol que piden.
     *
     * El cuerpo manda "role":"ADMIN" a propósito. Jackson descarta la propiedad porque el
     * record no la declara, así que la petición entra igual y el usuario nace CONDUCTOR. Si
     * alguien vuelve a agregarle el campo al DTO, esta prueba se pone en rojo.
     */
    @Test
    @DisplayName("El registro público ignora el rol que le manden: siempre nace CONDUCTOR")
    void neverGrantsTheRequestedRoleOnPublicRegistration() throws Exception {
        String body =
                """
                {"email":"intruso@ecopedia.test","password":"unaClave123",
                 "fullName":"Intruso","role":"ADMIN"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CONDUCTOR"));

        /*
         * Y la consecuencia que importa: con esa cuenta el backoffice sigue cerrado. Se pide
         * un token emitido para ese usuario tal como quedó guardado, no uno fabricado.
         */
        User created = userRepository.findByEmail("intruso@ecopedia.test").orElseThrow();
        mockMvc.perform(get("/api/users")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer "
                                        + tokenProvider.generateToken(
                                                created.getId(), created.getEmail(), created.getRole())))
                .andExpect(status().isForbidden());
    }

    /*
     * El perfil propio es la única operación autenticada que no pide un rol: la responde
     * cualquiera que haya entrado, con sus propios datos. La regla sigue estando declarada
     * —`isAuthenticated()`— y por eso el rechazo es un 403 como el de todas las demás.
     */
    @Test
    @DisplayName("El perfil propio exige token, pero no exige un rol en particular")
    void keepsOwnProfileOpenToEveryAuthenticatedRole() throws Exception {
        mockMvc.perform(get("/api/users/profile")).andExpect(status().isForbidden());

        for (Role role : Role.values()) {
            givenUser(role.name().toLowerCase() + "@ecopedia.test", role);
            mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, bearer(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(role.name()));
        }
    }

    /*
     * La edición del perfil tiene la misma regla que la consulta —estar logueado, con
     * cualquier rol— y una propiedad más fuerte: alcanza SOLO al dueño del token. No hay id en
     * la URL que se pueda cambiar, así que lo que se prueba es que con dos usuarios en la base
     * el que se modifica es el que pidió, y que el otro queda intacto.
     */
    @Test
    @DisplayName("Editar el perfil exige token y solo alcanza al dueño del token")
    void updatesOnlyTheTokenOwnerProfile() throws Exception {
        String body = """
                {"fullName":"Nombre Editado"}
                """;

        mockMvc.perform(put("/api/users/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        User other = givenUser("otro@ecopedia.test", Role.CONDUCTOR);
        User owner = givenUser("duenio@ecopedia.test", Role.CONDUCTOR);

        mockMvc.perform(put("/api/users/profile")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer "
                                        + tokenProvider.generateToken(owner.getId(), owner.getEmail(), owner.getRole()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nombre Editado"))
                .andExpect(jsonPath("$.email").value("duenio@ecopedia.test"));

        /* El de al lado no se tocó: es lo que separa "editó su perfil" de "editó un perfil". */
        User untouched = userService.getProfile(other.getId());
        assertEquals("Usuario CONDUCTOR", untouched.getFullName());
    }

    /*
     * El cambio de contraseña, con lo que lo hace seguro: exige la vigente. Sin ese campo, un
     * token robado o una sesión abierta en una máquina ajena alcanzarían para quedarse con la
     * cuenta, y el token solo no es prueba de que quien pide sea el dueño.
     */
    @Test
    @DisplayName("La contraseña se cambia solo con la actual correcta, y la nueva sirve para entrar")
    void changesPasswordOnlyWithTheCurrentOne() throws Exception {
        User owner = givenUser("clave@ecopedia.test", Role.CONDUCTOR);
        String bearer = "Bearer " + tokenProvider.generateToken(owner.getId(), owner.getEmail(), owner.getRole());

        mockMvc.perform(put("/api/users/profile/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"otraClave999\",\"newPassword\":\"claveNueva456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La contraseña actual no es correcta"));

        mockMvc.perform(put("/api/users/profile/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"unaClave123\",\"newPassword\":\"claveNueva456\"}"))
                .andExpect(status().isNoContent());

        /*
         * La prueba que importa: la nueva entra y la vieja ya no. Un 204 solo dice que la
         * operación se aceptó, no que haya guardado el hash nuevo.
         */
        userService.authenticate(new Credentials("clave@ecopedia.test", "claveNueva456"));
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.authenticate(new Credentials("clave@ecopedia.test", "unaClave123")));
    }

    /* Sin token no se llega, igual que a todo lo demás del perfil. */
    @Test
    @DisplayName("Cambiar la contraseña exige token")
    void rejectsPasswordChangeForAnonymousCallers() throws Exception {
        mockMvc.perform(put("/api/users/profile/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"unaClave123\",\"newPassword\":\"claveNueva456\"}"))
                .andExpect(status().isForbidden());
    }

    /*
     * Un nombre en blanco no es una edición: es borrar el nombre. La validación del DTO lo
     * rechaza con su mensaje, que es lo que el formulario muestra.
     */
    @Test
    @DisplayName("El nombre vacío se rechaza con 400 y con mensaje")
    void rejectsBlankNameOnProfileUpdate() throws Exception {
        User owner = givenUser("vacio@ecopedia.test", Role.CONDUCTOR);

        mockMvc.perform(put("/api/users/profile")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer "
                                        + tokenProvider.generateToken(owner.getId(), owner.getEmail(), owner.getRole()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El nombre completo es obligatorio"));
    }

    /*
     * Y la propiedad que va a sostener la pantalla de perfil: devuelve al dueño del token y no
     * a otro. Con dos usuarios en la base, un método que se equivoque de fila —el listado
     * filtrado en memoria, por ejemplo— falla acá y no en la demo.
     */
    @Test
    @DisplayName("El perfil propio devuelve al dueño del token, no al primero de la base")
    void answersWithTheTokenOwner() throws Exception {
        givenUser("primero@ecopedia.test", Role.ADMIN);
        User owner = givenUser("segundo@ecopedia.test", Role.CONDUCTOR);

        mockMvc.perform(get("/api/users/profile")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer "
                                        + tokenProvider.generateToken(
                                                owner.getId(), owner.getEmail(), owner.getRole())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("segundo@ecopedia.test"))
                .andExpect(jsonPath("$.role").value("CONDUCTOR"));
    }
}
