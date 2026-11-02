package com.ecopedia.simulation.paymentgateway.config;

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
 * Pide la clave de acceso en cada llamada a la API ({@code Authorization: Bearer <clave>}).
 *
 * <p><b>Por qué hace falta, si escucha solo en 127.0.0.1.</b> Escuchar en la propia máquina deja
 * afuera a la red, pero no a los demás procesos de la máquina: cualquiera podría pedir un cobro o
 * un reintegro. Con la clave, solo le habla quien la tiene, que es Pagos. Es lo mismo que pide una
 * pasarela real con su <i>access token</i>.
 *
 * <p>La clave sale del .env ({@code ECOPEDIA_PAYMENT_GATEWAY_ACCESS_TOKEN}) y es la misma que lee
 * Pagos. Se compara en tiempo constante, para que el tiempo de respuesta no vaya revelando cuántos
 * caracteres coinciden.
 *
 * <p>Solo cubre {@code /v1/}: la consola de H2 de los perfiles de desarrollo queda afuera.
 */
@Component
public class AccessTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expected;

    public AccessTokenFilter(@Value("${simulation.payment-gateway.access-token}") String accessToken) {
        this.expected = accessToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        byte[] received = header != null && header.startsWith(BEARER_PREFIX)
                ? header.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        if (!MessageDigest.isEqual(expected, received)) {
            log.warn("Llamada sin clave de acceso válida: {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter()
                    .write(
                            "{\"status\":401,\"error\":\"unauthorized\",\"message\":\"Invalid access token\","
                                    + "\"cause\":[{\"code\":\"invalid_access_token\",\"description\":\"Invalid access token\"}]}");
            return;
        }
        chain.doFilter(request, response);
    }
}
