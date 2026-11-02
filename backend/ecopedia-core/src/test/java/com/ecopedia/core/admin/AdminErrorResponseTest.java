package com.ecopedia.core.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regresión del defecto DEF-05: el backoffice respondía 500 ante un id que no existe.
 *
 * <p>{@code AdminController} no tenía manejador de errores propio, y el de core
 * ({@code AuthExceptionHandler}) se limita al paquete {@code user.web}: la excepción del servicio
 * llegaba sin traducir. Desde el 02/11 la traduce {@code ApiExceptionHandler}, con el mismo 404
 * que una ruta inexistente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminErrorResponseTest {

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

    private String adminBearer() {
        User admin = userService.register(
                new RegistrationData("admin-def05@ecopedia.test", "unaClave123", "Admin DEF-05", Role.ADMIN));
        return "Bearer " + tokenProvider.generateToken(admin.getId(), admin.getEmail(), Role.ADMIN);
    }

    /* La baja de usuarios del backoffice se sacó el 02/11: la de /api/users pide la contraseña. */

    @Test
    @DisplayName("DEF-05 · Dar de baja desde el backoffice una estación inexistente responde 404, no 500")
    void deactivatingAnUnknownStationIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/admin/stations/999999").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound());
    }
}
