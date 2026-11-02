package com.ecopedia.charging.security;

import com.ecopedia.charging.web.ApiProblems;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Lo que recibe un pedido sin sesión válida a una operación protegida: 401 (QA-33).
 *
 * <p>"Sin sesión válida" es sin token, o con uno vencido, con la firma alterada, con
 * {@code alg=none} o firmado con otra clave: el filtro JWT no autentica ninguno de esos, el
 * pedido sigue como anónimo y la anotación {@code @PreAuthorize} lo rechaza acá.
 *
 * <p><b>Hasta el 02/11 era 403</b>, el punto de entrada por defecto de Spring. Se había dejado así
 * porque el front lo usaba para darse cuenta de que la sesión venció; el cliente HTTP trata igual
 * el 401 y el 403, así que el cambio no le mueve nada.
 *
 * <p>Va con {@code WWW-Authenticate: Bearer} (RFC 6750) y el cuerpo en Problem Details. El motivo
 * concreto (vencido, alterado) no se dice: no le sirve a quien tiene una sesión legítima y sí a
 * quien prueba tokens.
 */
@Component
public class UnauthorizedEntryPoint implements AuthenticationEntryPoint {

    static final String CHALLENGE = "Bearer realm=\"ecopedia\"";

    private final ObjectMapper objectMapper;

    public UnauthorizedEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE);
        ApiProblems.write(request, response, ApiProblems.unauthorized(), objectMapper);
    }
}
