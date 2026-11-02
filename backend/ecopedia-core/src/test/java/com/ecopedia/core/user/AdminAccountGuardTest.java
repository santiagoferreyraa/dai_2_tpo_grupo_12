package com.ecopedia.core.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.security.JwtTokenProvider;
import com.ecopedia.core.user.data.JpaUserRepository;
import com.ecopedia.core.user.domain.RegistrationData;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserRepository;
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
 * Las bajas y los cambios de rol desde el backoffice (decidido por Manuel el 02/11):
 *
 * <ul>
 *   <li>a una cuenta {@code ADMIN}, también la propia, no se la toca desde la aplicación (409);
 *   <li>a las demás, sí, con la contraseña del {@code ADMIN} que lo pide (400 si no es la suya);
 *   <li>con cinco contraseñas equivocadas seguidas, el {@code ADMIN} queda bloqueado (429).
 * </ul>
 *
 * <p>Cada test usa mails propios: el contador de intentos es un bean, y los tests que comparten
 * contexto de Spring comparten el contador.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminAccountGuardTest {

    private static final String PASSWORD = "claveDelAdmin1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserService userService;

    @Autowired
    private JpaUserRepository userRepository;

    /** El puerto del dominio: con el de Spring Data, {@code findById} es ambiguo. */
    @Autowired
    private UserRepository users;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    private User givenUser(String email, Role role) {
        return userService.register(new RegistrationData(email, PASSWORD, "Usuario " + role, role));
    }

    private String bearer(User user) {
        return "Bearer " + tokenProvider.generateToken(user.getId(), user.getEmail(), user.getRole());
    }

    private ResultActions deactivate(User admin, User target, String password) throws Exception {
        return mockMvc.perform(delete("/api/users/" + target.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(password == null ? "{}" : "{\"password\":\"%s\"}".formatted(password)));
    }

    private ResultActions changeRole(User admin, User target, Role role, String password) throws Exception {
        return mockMvc.perform(patch("/api/users/" + target.getId() + "/role")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"%s\",\"password\":\"%s\"}".formatted(role, password)));
    }

    private boolean isActive(User user) {
        return users.findById(user.getId()).orElseThrow().isActive();
    }

    private Role roleOf(User user) {
        return users.findById(user.getId()).orElseThrow().getRole();
    }

    @Test
    @DisplayName("Con su contraseña, el ADMIN da de baja a un conductor")
    void deactivatesANonAdminWithThePassword() throws Exception {
        User admin = givenUser("guard-admin-1@ecopedia.test", Role.ADMIN);
        User driver = givenUser("guard-driver-1@ecopedia.test", Role.CONDUCTOR);

        deactivate(admin, driver, PASSWORD).andExpect(status().isNoContent());

        assertThat(isActive(driver)).isFalse();
    }

    @Test
    @DisplayName("Con otra contraseña, o sin ninguna, la baja se rechaza con PASSWORD_INCORRECT")
    void rejectsAWrongOrMissingPassword() throws Exception {
        User admin = givenUser("guard-admin-2@ecopedia.test", Role.ADMIN);
        User driver = givenUser("guard-driver-2@ecopedia.test", Role.CONDUCTOR);

        deactivate(admin, driver, "otraClave999")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_INCORRECT"));
        deactivate(admin, driver, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_INCORRECT"));
        mockMvc.perform(delete("/api/users/" + driver.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_INCORRECT"));

        assertThat(isActive(driver)).isTrue();
    }

    @Test
    @DisplayName("A otro ADMIN no se lo da de baja desde la aplicación, ni con la contraseña correcta")
    void neverDeactivatesAnotherAdmin() throws Exception {
        User admin = givenUser("guard-admin-3@ecopedia.test", Role.ADMIN);
        User other = givenUser("guard-admin-3b@ecopedia.test", Role.ADMIN);

        deactivate(admin, other, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_PROTECTED"));

        assertThat(isActive(other)).isTrue();
    }

    @Test
    @DisplayName("Ni a sí mismo: el ADMIN no se da de baja ni se saca el rol")
    void neverTouchesItself() throws Exception {
        User admin = givenUser("guard-admin-4@ecopedia.test", Role.ADMIN);

        deactivate(admin, admin, PASSWORD).andExpect(status().isConflict());
        changeRole(admin, admin, Role.CONDUCTOR, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_PROTECTED"));

        assertThat(isActive(admin)).isTrue();
        assertThat(roleOf(admin)).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("A otro ADMIN no se le cambia el rol desde la aplicación")
    void neverDemotesAnotherAdmin() throws Exception {
        User admin = givenUser("guard-admin-5@ecopedia.test", Role.ADMIN);
        User other = givenUser("guard-admin-5b@ecopedia.test", Role.ADMIN);

        changeRole(admin, other, Role.CPO, PASSWORD).andExpect(status().isConflict());

        assertThat(roleOf(other)).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Promover pide la contraseña; promovido a ADMIN, ya no se lo puede bajar")
    void promotesWithThePasswordAndThenProtects() throws Exception {
        User admin = givenUser("guard-admin-6@ecopedia.test", Role.ADMIN);
        User driver = givenUser("guard-driver-6@ecopedia.test", Role.CONDUCTOR);

        changeRole(admin, driver, Role.CPO, "otraClave999")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_INCORRECT"));
        assertThat(roleOf(driver)).isEqualTo(Role.CONDUCTOR);

        changeRole(admin, driver, Role.CPO, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CPO"));
        changeRole(admin, driver, Role.ADMIN, PASSWORD).andExpect(status().isOk());

        changeRole(admin, driver, Role.CONDUCTOR, PASSWORD).andExpect(status().isConflict());
        assertThat(roleOf(driver)).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Cinco contraseñas equivocadas bloquean al ADMIN: después, ni la correcta pasa")
    void locksTheAdminAfterTooManyWrongPasswords() throws Exception {
        User admin = givenUser("guard-admin-7@ecopedia.test", Role.ADMIN);
        User driver = givenUser("guard-driver-7@ecopedia.test", Role.CONDUCTOR);

        for (int i = 0; i < 5; i++) {
            deactivate(admin, driver, "otraClave999").andExpect(status().isBadRequest());
        }

        deactivate(admin, driver, PASSWORD).andExpect(status().isTooManyRequests());
        assertThat(isActive(driver)).isTrue();
    }

    @Test
    @DisplayName("La baja del backoffice que no pedía contraseña ya no existe")
    void theBackOfficeShortcutIsGone() throws Exception {
        User admin = givenUser("guard-admin-8@ecopedia.test", Role.ADMIN);
        User driver = givenUser("guard-driver-8@ecopedia.test", Role.CONDUCTOR);

        mockMvc.perform(delete("/api/admin/users/" + driver.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());

        assertThat(isActive(driver)).isTrue();
    }
}
