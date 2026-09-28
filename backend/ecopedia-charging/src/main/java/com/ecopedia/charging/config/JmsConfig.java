package com.ecopedia.charging.config;

import com.ecopedia.charging.booking.domain.BookingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * Cómo viajan los mensajes que publica este artefacto: JSON en un {@code TextMessage}.
 *
 * <p><b>No con la serialización de Java</b>, que es lo que haría JMS por omisión con un objeto.
 * Esa obliga a que el consumidor tenga la MISMA clase, en el mismo paquete, y Notificaciones vive
 * en otro artefacto: habría que compartir un módulo Maven solo para esto, y cualquier cambio en el
 * record rompería al otro lado en ejecución. Con JSON cada lado tiene su propia clase y lo único
 * compartido es el formato. Además se lee en la consola de Artemis.
 *
 * <p><b>El tipo viaja como un nombre, no como una clase.</b> El conversor escribe en la propiedad
 * {@code _type} un identificador lógico —{@value #BOOKING_EVENT_TYPE}— en vez del nombre completo de
 * la clase Java, y Notificaciones mapea ese mismo nombre a su propia clase.
 */
@Configuration
public class JmsConfig {

    /** El nombre con el que Notificaciones reconoce un evento de reserva. Es parte del contrato. */
    public static final String BOOKING_EVENT_TYPE = "booking-event";

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
        converter.setTypeIdMappings(Map.of(BOOKING_EVENT_TYPE, BookingEvent.class));
        return converter;
    }
}
