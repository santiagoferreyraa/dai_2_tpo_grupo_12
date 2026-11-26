package com.ecopedia.async.stats.messaging;

import com.ecopedia.async.config.JmsConfig;
import com.ecopedia.async.stats.domain.AnalyticsService;
import com.ecopedia.async.stats.domain.StatsTelemetryReading;
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
 * Segundo suscriptor independiente del tópico {@code telemetry.charging} (ECO-301, RF16, RNF05).
 *
 * <p>Usa la suscripción durable {@code ecopedia-stats} para recibir todas las lecturas publicadas
 * por los cargadores en paralelo a {@code ecopedia-charging}, sin competir ni quitarle mensajes.
 */
@Component
public class StatsTelemetryListener {

    private static final Logger log = LoggerFactory.getLogger(StatsTelemetryListener.class);
    private static final String DELIVERY_COUNT = "JMSXDeliveryCount";

    private final AnalyticsService analyticsService;
    private final MessageConverter converter;
    private final int maxDeliveryAttempts;

    public StatsTelemetryListener(
            AnalyticsService analyticsService,
            MessageConverter converter,
            @Value("${ecopedia.telemetry.max-delivery-attempts}") int maxDeliveryAttempts) {
        this.analyticsService = analyticsService;
        this.converter = converter;
        this.maxDeliveryAttempts = maxDeliveryAttempts;
    }

    @JmsListener(
            destination = "${ecopedia.telemetry.topic}",
            subscription = "${ecopedia.telemetry.subscription}",
            containerFactory = JmsConfig.TOPIC_LISTENER_FACTORY)
    public void onTelemetry(Message message) throws JMSException {
        int attempt = message.propertyExists(DELIVERY_COUNT) ? message.getIntProperty(DELIVERY_COUNT) : 1;

        StatsTelemetryReading reading;
        try {
            reading = read(message);
        } catch (JMSException | RuntimeException unreadable) {
            String jmsId = message.getJMSMessageID();
            reportFailure("ilegible " + (jmsId != null ? jmsId : "(sin id de JMS)"), attempt, unreadable);
            throw unreadable;
        }

        try {
            analyticsService.recordTelemetry(reading);
        } catch (RuntimeException failure) {
            reportFailure(String.valueOf(reading.eventId()), attempt, failure);
            throw failure;
        }
    }

    private StatsTelemetryReading read(Message message) throws JMSException {
        Object body = converter.fromMessage(message);
        if (body instanceof StatsTelemetryReading reading) {
            return reading;
        }
        throw new MessageConversionException(
                "Se esperaba un telemetry-event y llegó " + body.getClass().getName());
    }

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
