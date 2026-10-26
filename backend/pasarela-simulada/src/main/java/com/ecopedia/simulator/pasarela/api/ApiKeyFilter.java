package com.ecopedia.simulator.pasarela.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Exige la clave secreta del comercio en cada pedido: {@code Authorization: Bearer <clave>}.
 *
 * <p>Las pasarelas reales identifican así al comercio que cobra, y simularlo tiene un efecto
 * concreto: la clave es una configuración más que el Adapter de Ecopedia tiene que llevar, y un
 * 401 es un error más que tiene que saber interpretar.
 *
 * <p>Se compara en tiempo constante para no filtrar, por lo que tarda la respuesta, cuántos
 * caracteres de la clave se acertaron.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final byte[] expected;

    public ApiKeyFilter(@Value("${pasarela.api-key}") String apiKey) {
        this.expected = ("Bearer " + apiKey).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        byte[] received = header == null ? new byte[0] : header.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expected, received)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter()
                    .write("{\"error\":{\"type\":\"authentication_error\",\"code\":\"invalid_api_key\","
                            + "\"message\":\"Missing or invalid API key\"}}");
            return;
        }

        chain.doFilter(request, response);
    }
}
