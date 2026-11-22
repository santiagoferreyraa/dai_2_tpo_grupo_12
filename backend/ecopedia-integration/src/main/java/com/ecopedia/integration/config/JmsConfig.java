package com.ecopedia.integration.config;

import com.ecopedia.integration.payment.domain.DebtEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * Cómo viajan los avisos de deudas que publica Pagos (ECO-62): JSON en un {@code TextMessage}, igual
 * que los de Reservas en {@code ecopedia-charging}.
 *
 * <p><b>JSON y no la serialización de Java</b>, por lo mismo que allá: Notificaciones vive en otro
 * artefacto con su propia clase, y lo único compartido es el formato.
 *
 * <p><b>El tipo viaja como un nombre, no como una clase:</b> {@value #DEBT_EVENT_TYPE} en la propiedad
 * {@code _type}, que Notificaciones mapea a su propia clase. Es distinto del de las reservas
 * ({@code booking-event}) porque son dos contratos distintos por la misma cola.
 */
@Configuration
public class JmsConfig {

    /** El nombre con el que Notificaciones reconoce un evento de deuda. Es parte del contrato. */
    public static final String DEBT_EVENT_TYPE = "debt-event";

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
        converter.setTypeIdMappings(Map.of(DEBT_EVENT_TYPE, DebtEvent.class));
        return converter;
    }
}
