package com.ecopedia.charging.config;

import com.ecopedia.charging.booking.domain.BookingEvent;
import com.ecopedia.charging.session.messaging.ChargingTelemetryMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.ConnectionFactory;
import java.util.Map;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * Cómo viajan los mensajes que publica y consume este artefacto: JSON en un {@code TextMessage}.
 *
 * <p><b>No con la serialización de Java</b>, que es lo que haría JMS por omisión con un objeto.
 * Esa obliga a que el consumidor tenga la MISMA clase, en el mismo paquete. Con JSON cada lado
 * tiene su propia clase y lo único compartido es el formato y el tipo lógico en {@code _type}.
 */
@Configuration
@EnableJms
public class JmsConfig {

    /** El nombre con el que Notificaciones reconoce un evento de reserva. Es parte del contrato. */
    public static final String BOOKING_EVENT_TYPE = "booking-event";

    /** El nombre con el que el simulador publica y charging consume lecturas de telemetría (ECO-45, ECO-55). */
    public static final String TELEMETRY_EVENT_TYPE = "telemetry-event";

    /**
     * Contenedor de listeners JMS para suscripciones a tópicos (pub/sub).
     */
    @Bean
    public DefaultJmsListenerContainerFactory topicJmsListenerContainerFactory(
            DefaultJmsListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setPubSubDomain(true);
        return factory;
    }

    /**
     * Spring Boot toma este bean para el {@code JmsTemplate}. Usa el {@link ObjectMapper} de la
     * aplicación para que los {@code Instant} salgan como texto ISO-8601 y no como números.
     */
    @Bean
    public MessageConverter jmsMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setObjectMapper(objectMapper);
        converter.setTypeIdPropertyName("_type");
        converter.setTypeIdMappings(Map.of(
                BOOKING_EVENT_TYPE, BookingEvent.class,
                TELEMETRY_EVENT_TYPE, ChargingTelemetryMessage.class));
        return converter;
    }
}
