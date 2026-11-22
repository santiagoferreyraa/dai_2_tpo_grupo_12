package com.ecopedia.core.security;

import com.ecopedia.core.web.ApiProblems;
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
 * Las rutas {@code /internal/**} solo las llaman otros artefactos de Ecopedia, con la clave de
 * servicio ({@code Authorization: Bearer <clave>}). Es el mismo filtro que ecopedia-integration
 * (decidido por Manuel el 28/10, ECO-52), y llega a core con ECO-44: SesionesDeCarga le pregunta a
 * Terminales qué conector tiene el código que escaneó el conductor.
 *
 * <p>Por qué la clave de servicio y no el token del conductor: con su token, cualquier conductor
 * podría recorrer los códigos y saber cuál abre cada terminal sin estar frente a ninguna. La clave
 * sale del .env ({@code ECOPEDIA_INTERNAL_TOKEN}), se compara en tiempo constante y el proxy de Vite
 * nunca manda estas rutas: el navegador no llega.
 *
 * <p>Es un filtro de servlet y no una regla de Spring Security por lo mismo que en integration: la
 * autorización de este artefacto vive en los {@code @PreAuthorize}, pensados para usuarios, y un
 * token de usuario válido no tiene que abrir {@code /internal/**}.
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
            // Problem Details con el code estable, como el resto de los errores (QA-31).
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
            problem.setProperty("code", "INVALID_SERVICE_TOKEN");
            ApiProblems.write(request, response, problem, objectMapper);
            return;
        }
        chain.doFilter(request, response);
    }
}
