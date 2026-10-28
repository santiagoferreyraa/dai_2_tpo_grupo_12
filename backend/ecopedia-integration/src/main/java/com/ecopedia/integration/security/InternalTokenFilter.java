package com.ecopedia.integration.security;

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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Las rutas {@code /internal/**} solo las llaman otros artefactos de Ecopedia, con la clave de
 * servicio ({@code Authorization: Bearer <clave>}).
 *
 * <p><b>Por qué una clave de servicio y no el token del conductor.</b> El checkout le pregunta a
 * Pagos con el token del conductor porque consulta sus propias tarjetas. Lo que SesionesDeCarga le
 * pregunta a RedEléctrica —si el nodo aguanta una carga rápida— no es un dato del conductor, y con
 * su token cualquier conductor podría consultar o saturar al sistema legado por su cuenta. Decidido
 * por Manuel el 28/10 (ECO-52).
 *
 * <p>La autorización de este artefacto vive en los {@code @PreAuthorize} de cada método, pensados
 * para usuarios; esto es otra cosa, y por eso es un filtro: un token de conductor válido no abre
 * {@code /internal/**}, porque no es la clave. La clave sale del .env ({@code ECOPEDIA_INTERNAL_TOKEN}),
 * se compara en tiempo constante y el proxy de Vite nunca manda estas rutas: el navegador no llega.
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expected;

    public InternalTokenFilter(@Value("${ecopedia.internal.token}") String token) {
        this.expected = token.getBytes(StandardCharsets.UTF_8);
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
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":\"INVALID_SERVICE_TOKEN\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
