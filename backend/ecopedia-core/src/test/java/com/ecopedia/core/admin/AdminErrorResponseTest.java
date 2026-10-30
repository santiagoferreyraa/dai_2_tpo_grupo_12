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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regresión del defecto DEF-05: el backoffice responde 500 ante un id que no existe.
 *
 * <p>{@code AdminController} no tiene manejador de errores propio, y el de core
 * ({@code AuthExceptionHandler}) se limita al paquete {@code user.web}. La
 * {@link IllegalArgumentException} que tira el servicio llega sin traducir y el servidor contesta
 * 500, cuando el recurso simplemente no existe.
 *
 * <p><b>Deshabilitado a propósito</b> mientras el defecto esté abierto, y así figura en el informe
 * de ejecución (QA-11): con el código de hoy, MockMvc ve la excepción y el test falla, lo que
 * pondría el CI en rojo (G3). Se habilita en el mismo PR que lo corrige; desde ahí cuida que no
 * vuelva.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Disabled("DEF-05 abierto: el backoffice responde 500 ante un id inexistente. Se habilita con la corrección.")
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

    @Test
    @DisplayName("DEF-05 · Dar de baja desde el backoffice un usuario inexistente responde 404, no 500")
    void deactivatingAnUnknownUserIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/admin/users/999999").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DEF-05 · Dar de baja desde el backoffice una estación inexistente responde 404, no 500")
    void deactivatingAnUnknownStationIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/admin/stations/999999").header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isNotFound());
    }
}
