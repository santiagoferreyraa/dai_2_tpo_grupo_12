package com.ecopedia.core.security;

import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserRepository;
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
 * Filtro de autenticación JWT para peticiones HTTP.
 *
 * <p><b>La firma no alcanza: el usuario tiene que seguir existiendo y activo.</b> El token dura
 * horas, y antes el filtro le creía todo lo que decía. Un usuario dado de baja seguía entrando
 * hasta que el token venciera, y a un {@code ADMIN} al que le sacaban el rol le seguía valiendo el
 * de antes. Ahora, con la firma verificada, se busca al usuario en la base: si no está o está dado
 * de baja, la petición sigue como anónima, y el rol sale de la base y no del token.
 *
 * <p>Cuesta una consulta por clave primaria por petición autenticada, que es lo más barato que
 * tiene la base. Los otros tres artefactos no pueden hacer lo mismo, porque la tabla de usuarios
 * es de core: ahí el techo es la vida corta del token ({@code ecopedia.jwt.expiration-ms}).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = getJwtFromRequest(request);

        if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
            Long userId = tokenProvider.getUserIdFromToken(token);

            userRepository.findById(userId).filter(User::isActive).ifPresent(user -> {
                SimpleGrantedAuthority authority =
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of(authority));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
