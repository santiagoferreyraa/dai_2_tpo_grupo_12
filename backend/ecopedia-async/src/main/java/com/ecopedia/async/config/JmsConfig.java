package com.ecopedia.async.config;

import com.ecopedia.async.notification.messaging.BookingEventMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.ConnectionFactory;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(JmsConfig.class);

    /**
     * El contenedor de los {@code @JmsListener}, armado igual que el de Spring Boot —el configurer
     * le pone la sesión transaccional, que es lo que hace que un fallo devuelva el mensaje a la
     * cola— más un {@code ErrorHandler}.
     *
     * <p>Sin el {@code ErrorHandler}, cada fallo deja además un WARN del contenedor con la traza
     * entera: cinco por mensaje. El registro que sirve ya lo deja {@code BookingEventListener}, con
     * el id del mensaje y el número de intento; acá queda en DEBUG, por si hace falta la traza.
     * No cambia nada del reintento: el rollback ocurre antes de llegar al handler.
     */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            DefaultJmsListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setErrorHandler(failure -> log.debug("Fallo del listener de JMS (ya registrado)", failure));
        return factory;
    }

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
