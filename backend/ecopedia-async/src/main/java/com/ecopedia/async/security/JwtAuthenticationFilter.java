package com.ecopedia.async.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Pone en el contexto de seguridad al usuario que viene firmado en el encabezado.
 *
 * <p>El principal es el id del usuario, igual que en Pagos: los avisos se guardan por id de
 * destinatario, y este artefacto no tiene con qué resolver un email a un usuario.
 *
 * <p>Sin token, o con uno inválido, la petición sigue como anónima y la rechaza el
 * {@code @PreAuthorize} del método. Ver el filtro de {@code ecopedia-integration}, que explica
 * por qué no se corta acá.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenReader tokenReader;

    public JwtAuthenticationFilter(JwtTokenReader tokenReader) {
        this.tokenReader = tokenReader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = bearerTokenOf(request);

        if (StringUtils.hasText(token)) {
            tokenReader.read(token).ifPresent(claims -> authenticate(claims, request));
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        String role = claims.get("role", String.class);
        if (!StringUtils.hasText(role)) return;

        var authentication = new UsernamePasswordAuthenticationToken(
                claims.getSubject(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String bearerTokenOf(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
