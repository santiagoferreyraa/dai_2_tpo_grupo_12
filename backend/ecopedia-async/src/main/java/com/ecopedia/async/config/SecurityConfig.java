package com.ecopedia.async.config;

import com.ecopedia.async.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguridad del artefacto {@code ecopedia-async}.
 *
 * <p>El mismo criterio que en los otros tres: la cadena deja pasar todas las URL y quien decide es
 * el {@code @PreAuthorize} de cada método, y sin sesión de servidor porque se autentica por token.
 *
 * <p>La seguridad cubre solo la entrada HTTP. Lo que llega por el broker no pasa por acá: el
 * listener no atiende a un usuario sino a otro componente del sistema, y el destinatario de cada
 * aviso viene escrito en el mensaje.
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // X-Frame-Options en SAMEORIGIN y no apagado: la consola H2 se dibuja en un <iframe> de su
        // propia página y sigue andando, pero ningún sitio ajeno puede embeber la aplicación para
        // engañar clics. La consola en sí existe solo en los perfiles dev y local.
        return http.csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.requestMatchers("/h2-console/**")
                        .permitAll()
                        .anyRequest()
                        .permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
