package com.ecopedia.charging.config;

import com.ecopedia.charging.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguridad del artefacto {@code ecopedia-charging}: JWT emitido por core y
 * {@code @PreAuthorize} en los controladores.
 *
 * <p>Sigue el mismo esquema que el {@code SecurityConfig} de core —la cadena deja pasar todas las
 * URL y quien decide es la anotación sobre el método— para que las reglas de los dos artefactos
 * se lean igual y respondan igual.
 *
 * <p><b>Sin sesión HTTP, y no es un detalle.</b> La identidad viaja entera en el token, así que
 * el servidor no guarda nada del usuario entre pedidos. El estado de Reservas (las retenciones)
 * lo sostiene el componente, no una sesión: son dos cosas distintas, y conviene no mezclarlas en
 * la oral.
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
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Un {@code UserDetailsService} vacío, a propósito.
     *
     * <p>Sin ningún bean de este tipo, Spring Boot crea un usuario "user" con una contraseña
     * aleatoria y la imprime en el log al arrancar. Acá nadie se loguea con usuario y contraseña
     * —eso es de core—, así que se declara uno sin usuarios y ese aviso engañoso no aparece.
     */
    @Bean
    public UserDetailsService noLocalUsers() {
        return new InMemoryUserDetailsManager();
    }
}
