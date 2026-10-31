package com.ecopedia.charging.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Las tareas periódicas del artefacto: el barrido de la incomparecencia (ECO-57) y el de las señas
 * por devolver (ECO-53).
 *
 * <p>Se pueden apagar con {@code ecopedia.scheduling.enabled=false}, para un test que quiera ser el
 * único que mueve las reservas. Encendidas por defecto: sin ellas nadie marca un no-show.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "ecopedia.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {}
