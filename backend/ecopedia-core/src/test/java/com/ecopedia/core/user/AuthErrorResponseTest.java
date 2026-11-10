package com.ecopedia.core.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.user.data.JpaUserRepository;
import com.ecopedia.core.user.domain.RegistrationData;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Los errores de registro y login llegan con su mensaje (ECO-36).
 *
 * <p><b>Por qué existe.</b> Sin {@code AuthExceptionHandler} estos cinco casos respondían 500
 * —o 400 sin cuerpo aprovechable—, así que el frontend solo podía mostrar "Error 500". El
 * problema no lo veía ningún test: el servicio devuelve la excepción correcta y
 * {@code UserServiceTest} la afirma, pero entre el servicio y el navegador se perdía. Por eso
 * las pruebas van contra la capa web y no contra el servicio.
 *
 * <p><b>El login no distingue mail inexistente de contraseña equivocada</b>, y los dos primeros
 * casos lo dejan escrito: si el mensaje diferenciara, cualquiera podría averiguar qué
 * direcciones están registradas probando de a una.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthErrorResponseTest {

    private static final String REGISTERED_EMAIL = "registrado@ecopedia.test";
    private static final String REGISTERED_PASSWORD = "unaClave123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JpaUserRepository userRepository;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    private void givenRegisteredUser() {
        userService.register(
                new RegistrationData(REGISTERED_EMAIL, REGISTERED_PASSWORD, "Usuario Registrado", Role.CONDUCTOR));
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(
                post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("Un login con un email que no existe responde 400 y no 500")
    void loginWithUnknownEmailAnswersBadRequest() throws Exception {
        postJson(
                        "/api/auth/login",
                        """
                {"email":"nadie@ecopedia.test","password":"unaClave123"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
    }

    @Test
    @DisplayName("Un login con la contraseña equivocada da el mismo mensaje que uno con email inexistente")
    void loginWithWrongPasswordAnswersTheSameMessage() throws Exception {
        givenRegisteredUser();

        postJson(
                        "/api/auth/login",
                        """
                {"email":"%s","password":"otraClave999"}
                """
                                .formatted(REGISTERED_EMAIL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
    }

    @Test
    @DisplayName("Registrarse con un email ya usado responde 400 explicando el motivo")
    void registerWithTakenEmailExplainsWhy() throws Exception {
        givenRegisteredUser();

        postJson(
                        "/api/auth/register",
                        """
                {"email":"%s","password":"unaClave123","fullName":"Otro Usuario"}
                """
                                .formatted(REGISTERED_EMAIL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ya está registrado")));
    }

    @Test
    @DisplayName("Un email mal formado devuelve el mensaje de la anotación, no un 400 pelado")
    void malformedEmailReturnsItsValidationMessage() throws Exception {
        postJson(
                        "/api/auth/login",
                        """
                {"email":"noesunmail","password":"unaClave123"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Formato de email inválido"));
    }

    @Test
    @DisplayName("Una contraseña demasiado corta devuelve el mensaje de la anotación")
    void shortPasswordReturnsItsValidationMessage() throws Exception {
        postJson(
                        "/api/auth/register",
                        """
                {"email":"nuevo@ecopedia.test","password":"corta","fullName":"Usuario Nuevo"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La contraseña debe tener al menos 8 caracteres"));
    }

    @Test
    @DisplayName("Siete caracteres no alcanzan: el mínimo es 8, como pide ASVS 5.0 (DEF-11)")
    void sevenCharactersAreNotEnough() throws Exception {
        postJson(
                        "/api/auth/register",
                        """
                {"email":"siete@ecopedia.test","password":"1234567","fullName":"Usuario Nuevo"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La contraseña debe tener al menos 8 caracteres"));
    }
}
