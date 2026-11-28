package com.ecopedia.async.config;

import com.ecopedia.async.security.JwtAuthenticationFilter;
import com.ecopedia.async.security.NotFoundAccessDeniedHandler;
import com.ecopedia.async.security.UnauthorizedEntryPoint;
import com.ecopedia.async.web.ApiProblems;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

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
                /*
                 * Las lecturas quedan abiertas y quien decide es la anotación de cada método, como
                 * siempre. Las escrituras, en cambio, piden sesión ya acá: así un anónimo recibe 401
                 * antes de que se lea el cuerpo, y no un 400 que le cuente qué campos se esperan
                 * (la validación del @RequestBody corre antes que el @PreAuthorize).
                 * Las únicas escrituras sin sesión son las de /internal/**, que tienen su propia
                 * clave de servicio (InternalTokenFilter).
                 */
                .authorizeHttpRequests(requests -> requests.requestMatchers(HttpMethod.GET, "/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers("/internal/**", "/h2-console/**", "/error")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
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
