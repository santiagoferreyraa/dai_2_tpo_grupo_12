package com.ecopedia.core.pricing.service;

import com.ecopedia.core.pricing.domain.PricingRules;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Las reglas de Tarificación salen de {@code ecopedia.pricing} en el {@code application.yml}, y la hora,
 * de un {@link Clock}: el instante de una carga se decide contra el reloj del contenedor, y un test lo
 * puede fijar.
 */
@Configuration
@EnableConfigurationProperties(PricingRules.class)
public class PricingConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
