package com.ecopedia.charging.config;

import com.ecopedia.charging.security.JwtAuthenticationFilter;
import com.ecopedia.charging.security.NotFoundAccessDeniedHandler;
import com.ecopedia.charging.security.UnauthorizedEntryPoint;
import com.ecopedia.charging.web.ApiProblems;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

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

    static final String API_CONTENT_SECURITY_POLICY =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final NotFoundAccessDeniedHandler accessDeniedHandler;
    private final UnauthorizedEntryPoint unauthorizedEntryPoint;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            NotFoundAccessDeniedHandler accessDeniedHandler,
            UnauthorizedEntryPoint unauthorizedEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.accessDeniedHandler = accessDeniedHandler;
        this.unauthorizedEntryPoint = unauthorizedEntryPoint;
    }

    @Bean
    /*
     * CSRF apagado a propósito (SonarCloud S4502, revisado): la API no usa cookies ni sesión de
     * servidor (STATELESS) y el token viaja en la cabecera Authorization, que un sitio ajeno no
     * puede hacer que el navegador mande solo. Sin credencial automática no hay CSRF posible.
     */
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // X-Frame-Options en SAMEORIGIN y no apagado: la consola H2 se dibuja en un <iframe> de su
        // propia página y sigue andando, pero ningún sitio ajeno puede embeber la aplicación para
        // engañar clics. La consola en sí existe solo en los perfiles dev y local.
        return http.csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())
                        // QA-32: la API no devuelve nada que un navegador tenga que ejecutar, así
                        // que su CSP no permite nada. Solo en /api: una política así rompería la
                        // consola H2 de los perfiles dev y local.
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                                new StaticHeadersWriter("Content-Security-Policy", API_CONTENT_SECURITY_POLICY))))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                // Con sesión y sin el rol, un 404 como el de una ruta que no existe; el 403 va
                // al log. Ver NotFoundAccessDeniedHandler.
                // Sin sesión válida, 401 (UnauthorizedEntryPoint). Con sesión y sin el rol, un 404
                // como el de una ruta que no existe; el 403 va al log (NotFoundAccessDeniedHandler).
                .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorizedEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
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

    /**
     * Una URL que el firewall de Spring Security rechaza (un carácter de control codificado, un
     * {@code //}, un {@code ;}) sale como los demás errores: 400 en Problem Details. Sin esto la
     * contestaba el {@code /error} de Spring Boot con su propio JSON (lo encontró Schemathesis, QA-25).
     */
    @Bean
    public RequestRejectedHandler requestRejectedHandler(ObjectMapper objectMapper) {
        return (request, response, rejected) ->
                ApiProblems.write(request, response, ApiProblems.invalidRequest(), objectMapper);
    }
}
