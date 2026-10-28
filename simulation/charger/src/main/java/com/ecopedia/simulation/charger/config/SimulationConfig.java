package com.ecopedia.simulation.charger.config;

import com.ecopedia.simulation.charger.domain.TelemetryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * El reloj, el azar y el formato de los mensajes, como beans: los tests fijan los dos primeros y
 * leen el tercero tal como lo va a leer el suscriptor.
 */
@Configuration
public class SimulationConfig {

    /** El nombre con el que un suscriptor reconoce una lectura. Es parte del contrato del tópico. */
    public static final String TELEMETRY_EVENT_TYPE = "telemetry-event";

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** De acá sale el porcentaje de batería con que llega el auto, cuando el pedido no lo fija. */
    @Bean
    public RandomGenerator randomGenerator() {
        return RandomGenerator.getDefault();
    }

    /**
     * JSON en un {@code TextMessage}, con el tipo lógico en {@code _type}: el mismo formato que usa
     * Reservas para la cola de avisos, y por lo mismo (ver {@code JmsConfig} de ecopedia-charging).
     * Cada suscriptor tiene su propia clase y lo único compartido es el formato.
     */
    @Bean
    public MessageConverter jmsMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setObjectMapper(objectMapper);
        converter.setTypeIdPropertyName("_type");
        converter.setTypeIdMappings(Map.of(TELEMETRY_EVENT_TYPE, TelemetryEvent.class));
        return converter;
    }
}
