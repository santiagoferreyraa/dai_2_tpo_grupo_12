package com.ecopedia.charging.config;

import com.ecopedia.charging.booking.domain.BookingEvent;
import com.ecopedia.charging.session.domain.TelemetryReading;
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
 * Cómo viajan los mensajes que publica y recibe este artefacto: JSON en un {@code TextMessage}.
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

    /** El nombre con el que el cargador marca una lectura de telemetría. Es parte de su contrato. */
    public static final String TELEMETRY_EVENT_TYPE = "telemetry-event";

    /** El contenedor de los listeners que escuchan un tópico: {@link #topicListenerContainerFactory}. */
    public static final String TOPIC_LISTENER_FACTORY = "topicListenerContainerFactory";

    private static final Logger log = LoggerFactory.getLogger(JmsConfig.class);

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
        converter.setTypeIdMappings(
                Map.of(BOOKING_EVENT_TYPE, BookingEvent.class, TELEMETRY_EVENT_TYPE, TelemetryReading.class));
        return converter;
    }

    /**
     * El contenedor para escuchar un <b>tópico</b> con una suscripción <b>durable y compartida</b>.
     *
     * <p><b>Durable</b>, para que las lecturas publicadas mientras este artefacto estaba caído
     * lleguen cuando vuelve: sin eso, un reinicio en medio de una carga perdería lecturas. Vale
     * desde la primera vez que el artefacto se conecta, que es cuando el broker crea la suscripción.
     *
     * <p><b>Compartida</b> (JMS 2.0), para no fijar un {@code clientId} en la conexión: la conexión
     * es la misma con la que se publican los avisos, y una segunda instancia del artefacto se
     * repartiría las lecturas con esta en vez de recibirlas duplicadas.
     *
     * <p>El configurer le pone lo mismo que al contenedor por omisión, incluida la sesión
     * transaccional: si procesar una lectura falla, el broker la vuelve a entregar. El
     * {@code ErrorHandler} deja en DEBUG la traza que el contenedor escribiría en cada fallo; el
     * registro útil lo deja el listener.
     */
    @Bean(TOPIC_LISTENER_FACTORY)
    public DefaultJmsListenerContainerFactory topicListenerContainerFactory(
            DefaultJmsListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setPubSubDomain(true);
        factory.setSubscriptionDurable(true);
        factory.setSubscriptionShared(true);
        factory.setErrorHandler(failure -> log.debug("Fallo del listener de JMS (ya registrado)", failure));
        return factory;
    }
}
