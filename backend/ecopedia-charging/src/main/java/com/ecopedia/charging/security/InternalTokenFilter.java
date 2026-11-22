package com.ecopedia.charging.security;

import com.ecopedia.charging.web.ApiProblems;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Las rutas {@code /internal/**} de este artefacto solo las llaman otros artefactos de Ecopedia, con
 * la clave de servicio ({@code Authorization: Bearer <clave>}). Hoy, una sola: Pagos pidiendo que se
 * anulen las reservas de una cuenta marcada por deuda (ECO-62).
 *
 * <p>Es el mismo filtro que el de ecopedia-integration, y por lo mismo: un token de conductor válido no
 * abre {@code /internal/**}, porque no es la clave. La clave sale del .env ({@code ECOPEDIA_INTERNAL_TOKEN}),
 * se compara en tiempo constante, y ni Caddy ni el proxy de Vite mandan estas rutas: el navegador no
 * llega.
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expected;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(@Value("${ecopedia.internal.token}") String token, ObjectMapper objectMapper) {
        this.expected = token.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        byte[] received = header != null && header.startsWith(BEARER_PREFIX)
                ? header.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        if (!MessageDigest.isEqual(expected, received)) {
            log.warn("Llamada interna sin la clave de servicio: {} {}", request.getMethod(), request.getRequestURI());
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
            problem.setProperty("code", "INVALID_SERVICE_TOKEN");
            ApiProblems.write(request, response, problem, objectMapper);
            return;
        }
        chain.doFilter(request, response);
    }
}
