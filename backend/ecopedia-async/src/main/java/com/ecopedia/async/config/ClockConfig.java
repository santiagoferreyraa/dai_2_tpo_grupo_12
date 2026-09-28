package com.ecopedia.async.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El reloj con el que se fecha cada aviso y cada lectura.
 *
 * <p>Inyectado y no {@code Instant.now()} escrito en el código, por lo mismo que en los otros
 * módulos: para que una prueba pueda fijar la hora y afirmar sobre ella.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
