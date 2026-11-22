package com.ecopedia.integration.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Las tareas periódicas del artefacto: el barrido de la deuda (ECO-62).
 *
 * <p>Se pueden apagar con {@code ecopedia.scheduling.enabled=false}, para un test que quiera ser el
 * único que mueve las deudas. Encendidas por defecto: sin ellas nadie reintenta una deuda. Es el
 * mismo interruptor que en ecopedia-charging.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "ecopedia.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {}
