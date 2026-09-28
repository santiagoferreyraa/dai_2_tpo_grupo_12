package com.ecopedia.core.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.security.JwtTokenProvider;
import com.ecopedia.core.user.data.JpaUserRepository;
import com.ecopedia.core.user.domain.RegistrationData;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserService;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Lo que la firma del token no alcanza a garantizar, y el login sin freno.
 *
 * <p><b>Token.</b> Antes, un token bien firmado valía hasta vencer aunque el usuario ya estuviera
 * dado de baja, y el rol era el que decía el token aunque en la base fuera otro. Ahora core busca
 * al usuario en cada petición.
 *
 * <p><b>Login.</b> Con cinco fallos seguidos la cuenta se bloquea quince minutos, exista o no.
 *
 * <p>Cada test usa un email propio: el contador de intentos es un bean, y los tests que comparten
 * contexto de Spring comparten el contador.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SessionHardeningTest {

    private static final String PASSWORD = "unaClave123";

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

    private User givenUser(String email, Role role) {
        return userService.register(new RegistrationData(email, PASSWORD, "Usuario " + role, role));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """
                        .formatted(email, password)));
    }

    private String bearer(Long userId, String email, Role role) {
        return "Bearer " + tokenProvider.generateToken(userId, email, role);
    }

    @Test
    @DisplayName("El token de un usuario dado de baja deja de valer al instante, sin esperar que venza")
    void rejectsTokensOfDeactivatedUsers() throws Exception {
        User user = givenUser("baja@ecopedia.test", Role.CONDUCTOR);
        String token = bearer(user.getId(), user.getEmail(), user.getRole());

        mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        userService.deactivateUser(user.getId());

        mockMvc.perform(get("/api/users/profile").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El rol sale de la base: un token que dice ADMIN no abre el backoffice si el usuario ya no lo es")
    void takesTheRoleFromTheDatabase() throws Exception {
        User user = givenUser("degradado@ecopedia.test", Role.ADMIN);
        String token = bearer(user.getId(), user.getEmail(), Role.ADMIN);

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        userService.updateRole(user.getId(), Role.CONDUCTOR);

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un token bien firmado de un usuario que no existe no autentica a nadie")
    void rejectsTokensOfUnknownUsers() throws Exception {
        String token = bearer(987_654L, "fantasma@ecopedia.test", Role.ADMIN);

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Con cinco fallos la cuenta se bloquea: ni la contraseña correcta entra, y dice cuándo reintentar")
    void locksTheAccountAfterFiveFailures() throws Exception {
        givenUser("bloqueo@ecopedia.test", Role.CONDUCTOR);

        for (int i = 0; i < 5; i++) {
            login("bloqueo@ecopedia.test", "otraClave999").andExpect(status().isBadRequest());
        }

        login("bloqueo@ecopedia.test", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.detail").value("Demasiados intentos fallidos. Probá de nuevo en 15 minutos."));
    }

    @Test
    @DisplayName("Un email que no existe se bloquea igual: el bloqueo no delata qué cuentas hay")
    void locksUnknownEmailsToo() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("nadie-aca@ecopedia.test", "otraClave999").andExpect(status().isBadRequest());
        }

        login("nadie-aca@ecopedia.test", "otraClave999").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Un login correcto antes del máximo borra los fallos")
    void successBeforeTheLimitResetsTheCount() throws Exception {
        givenUser("distraido@ecopedia.test", Role.CONDUCTOR);

        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 4; i++) {
                login("distraido@ecopedia.test", "otraClave999").andExpect(status().isBadRequest());
            }
            login("distraido@ecopedia.test", PASSWORD).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("La baja se informa solo con la contraseña correcta: con una equivocada, el mensaje de siempre")
    void doesNotRevealDeactivatedAccountsWithoutThePassword() throws Exception {
        User user = givenUser("dada-de-baja@ecopedia.test", Role.CONDUCTOR);
        userService.deactivateUser(user.getId());

        login("dada-de-baja@ecopedia.test", "otraClave999")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));

        login("dada-de-baja@ecopedia.test", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El usuario está dado de baja"));
    }
}
