package com.ecopedia.charging.session.messaging;

import com.ecopedia.charging.config.JmsConfig;
import com.ecopedia.charging.session.domain.ChargingSessionService;
import com.ecopedia.charging.session.domain.TelemetryReading;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.stereotype.Component;

/**
 * La segunda entrada de SesionesDeCarga, además de REST: se suscribe al tópico
 * {@code telemetry.charging}, donde los cargadores publican sus lecturas (RF16, ECO-40).
 *
 * <p>Es capa de presentación, igual que el controlador: recibe, traduce y delega en
 * {@link ChargingSessionService#processTelemetry}. No decide nada sobre la sesión.
 *
 * <p><b>Tópico y no cola:</b> la misma lectura le sirve a más de uno —este componente y
 * Estadísticas—, y ninguno le puede sacar el mensaje al otro. Cada suscriptor tiene su suscripción,
 * con su nombre, y recibe todas las lecturas. La de este componente es durable (ver
 * {@link JmsConfig#topicListenerContainerFactory}).
 *
 * <p><b>Está desacoplado de habilitar y detener una carga:</b> esas dos operaciones no esperan a
 * este listener ni lo llaman. Si el broker está caído, la carga se detiene y se liquida igual, con
 * los kWh que informa el cargador al apagarse.
 *
 * <p><b>Qué pasa si algo falla.</b> La sesión de JMS es transaccional: si leer o guardar la lectura
 * lanza, el broker la vuelve a entregar, y al último intento la aparta en la cola de mensajes
 * muertos del tópico (la regla está en {@code docker/artemis/broker.properties} y vale para todo
 * destino). Una lectura repetida o atrasada no es una falla: el servicio la descarta. Recibe el
 * {@link Message} crudo para que una lectura ilegible también deje su registro, con el intento.
 */
@Component
public class TelemetryListener {

    private static final Logger log = LoggerFactory.getLogger(TelemetryListener.class);

    /** Lo pone Artemis en cada entrega: 1 la primera vez, 2 en el primer reintento, y así. */
    private static final String DELIVERY_COUNT = "JMSXDeliveryCount";

    private final ChargingSessionService chargingSessionService;
    private final MessageConverter converter;
    private final int maxDeliveryAttempts;

    public TelemetryListener(
            ChargingSessionService chargingSessionService,
            MessageConverter converter,
            @Value("${ecopedia.telemetry.max-delivery-attempts}") int maxDeliveryAttempts) {
        this.chargingSessionService = chargingSessionService;
        this.converter = converter;
        this.maxDeliveryAttempts = maxDeliveryAttempts;
    }

    @JmsListener(
            destination = "${ecopedia.telemetry.topic}",
            subscription = "${ecopedia.telemetry.subscription}",
            containerFactory = JmsConfig.TOPIC_LISTENER_FACTORY)
    public void onTelemetry(Message message) throws JMSException {
        int attempt = message.propertyExists(DELIVERY_COUNT) ? message.getIntProperty(DELIVERY_COUNT) : 1;

        TelemetryReading reading;
        try {
            reading = read(message);
        } catch (JMSException | RuntimeException unreadable) {
            String jmsId = message.getJMSMessageID();
            reportFailure("ilegible " + (jmsId != null ? jmsId : "(sin id de JMS)"), attempt, unreadable);
            throw unreadable;
        }

        try {
            chargingSessionService.processTelemetry(reading);
        } catch (RuntimeException failure) {
            reportFailure(String.valueOf(reading.eventId()), attempt, failure);
            throw failure;
        }
    }

    private TelemetryReading read(Message message) throws JMSException {
        Object body = converter.fromMessage(message);
        if (body instanceof TelemetryReading reading) {
            return reading;
        }
        throw new MessageConversionException(
                "Se esperaba un telemetry-event y llegó " + body.getClass().getName());
    }

    /** Un WARN por intento fallido y un ERROR en el último, que es el que Artemis aparta. */
    private void reportFailure(String readingId, int attempt, Exception failure) {
        if (attempt >= maxDeliveryAttempts) {
            log.error(
                    "La lectura {} falló en el intento {} de {} y pasa a la cola de mensajes muertos. Causa: {}",
                    readingId,
                    attempt,
                    maxDeliveryAttempts,
                    failure.toString());
        } else {
            log.warn(
                    "La lectura {} falló en el intento {} de {}; Artemis la vuelve a entregar. Causa: {}",
                    readingId,
                    attempt,
                    maxDeliveryAttempts,
                    failure.toString());
        }
    }
}
