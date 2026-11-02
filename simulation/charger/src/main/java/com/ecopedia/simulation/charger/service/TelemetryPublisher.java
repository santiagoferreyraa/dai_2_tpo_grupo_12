package com.ecopedia.simulation.charger.service;

import com.ecopedia.simulation.charger.domain.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

/**
 * Deja cada lectura en el tópico {@code telemetry.charging}.
 *
 * <p><b>Tópico y no cola</b> (ARQUITECTURA §3.3): la misma lectura la necesitan SesionesDeCarga y
 * Estadísticas, y ninguno le puede robar el mensaje al otro. El {@code JmsTemplate} publica en un
 * tópico porque {@code spring.jms.pub-sub-domain} está en {@code true}. Los mensajes son
 * persistentes, que es lo que permite que un suscriptor durable los reciba aunque estuviera caído
 * cuando salieron.
 *
 * <p>Además del cuerpo, se copian a propiedades del mensaje el tipo, el conector y la sesión: no los
 * necesita el suscriptor, que lee el cuerpo, pero sirven para filtrar con un selector y para leer de
 * un vistazo en la consola de Artemis qué lectura es cuál.
 */
@Component
public class TelemetryPublisher {

    private static final Logger log = LoggerFactory.getLogger(TelemetryPublisher.class);

    private final JmsTemplate jmsTemplate;
    private final String topic;

    public TelemetryPublisher(JmsTemplate jmsTemplate, @Value("${simulation.charger.telemetry-topic}") String topic) {
        this.jmsTemplate = jmsTemplate;
        this.topic = topic;
    }

    /** Publica la lectura. Si el broker no está, lanza la excepción de JMS: decide quien llama. */
    public void publish(TelemetryEvent event) {
        jmsTemplate.convertAndSend(topic, event, message -> {
            message.setStringProperty("eventType", event.type().name());
            message.setLongProperty("connectorId", event.connectorId());
            message.setStringProperty("sessionId", event.sessionId());
            return message;
        });
        log.debug("Lectura {} del conector {} publicada en {}", event.type(), event.connectorId(), topic);
    }

    /**
     * Publica la lectura, y si el broker no está deja un WARN y sigue.
     *
     * <p>Es lo que pasa con un cargador de verdad que pierde la conexión: sigue cargando. Como los
     * kWh van acumulados, la próxima lectura que salga trae el total correcto.
     */
    public void publishOrWarn(TelemetryEvent event) {
        try {
            publish(event);
        } catch (RuntimeException failure) {
            log.warn(
                    "No se pudo publicar la lectura {} del conector {} (sesión {}): {}",
                    event.type(),
                    event.connectorId(),
                    event.sessionId(),
                    failure.getMessage());
        }
    }
}
