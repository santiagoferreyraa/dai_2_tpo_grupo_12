package com.ecopedia.async.config;

import com.ecopedia.async.notification.messaging.BookingEventMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * Cómo se leen los mensajes que llegan: JSON en un {@code TextMessage}, el mismo formato con que los
 * publica {@code ecopedia-charging} (ver su {@code JmsConfig}).
 *
 * <p>El tipo llega como nombre lógico en la propiedad {@code _type}, y acá se mapea a la clase
 * propia de este artefacto. Un {@code _type} desconocido no se puede leer: el mensaje falla, el
 * broker lo reintenta y termina en la {@code DLQ}, que es donde tiene que ir a parar un mensaje que
 * nadie entiende.
 */
@Configuration
public class JmsConfig {

    /** Tiene que coincidir con {@code JmsConfig.BOOKING_EVENT_TYPE} de {@code ecopedia-charging}. */
    public static final String BOOKING_EVENT_TYPE = "booking-event";

    /** Spring Boot toma este bean para el contenedor de los {@code @JmsListener}. */
    @Bean
    public MessageConverter jmsMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setObjectMapper(objectMapper);
        converter.setTypeIdPropertyName("_type");
        converter.setTypeIdMappings(Map.of(BOOKING_EVENT_TYPE, BookingEventMessage.class));
        return converter;
    }
}
