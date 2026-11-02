package com.ecopedia.async.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ecopedia.async.EmbeddedBrokerSettings;
import com.ecopedia.async.notification.domain.NotificationService;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.UUID;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * La cola de mensajes muertos de Notificaciones (QA-28), contra un Artemis de verdad —el embebido
 * de los tests— con las reglas de {@code docker/artemis/broker.properties}.
 *
 * <p>Un mensaje que no se puede procesar —porque el servicio falla siempre o porque no se puede
 * leer— se entrega {@code max-delivery-attempts} veces, deja su registro y termina en
 * {@code DLQ.notifications.dispatch}, de donde se lo puede reenviar.
 *
 * <p>El servicio es un mock para poder hacerlo fallar a voluntad: lo que se prueba es el camino
 * del mensaje, no el aviso.
 */
@SpringBootTest
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
class DeadLetterQueueTest {

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private EmbeddedActiveMQ broker;

    @MockitoBean
    private NotificationService notificationService;

    @Value("${ecopedia.notifications.queue}")
    private String queue;

    @Value("${ecopedia.notifications.max-delivery-attempts}")
    private int maxDeliveryAttempts;

    /** El prefijo que Artemis le pone a la cola de mensajes muertos que crea por cada destino. */
    private static final String DEAD_LETTER_PREFIX = "DLQ.";

    /** La cola común que traen el broker de Docker y el embebido. */
    private static final SimpleString GENERIC_DLQ = SimpleString.of("DLQ");

    @Test
    @DisplayName("El broker usa las reglas del archivo de Docker, y el log cuenta los mismos intentos")
    void brokerUsesTheDeployedRules() {
        Properties file = brokerFile();
        int fileAttempts = Integer.parseInt(file.getProperty("addressSettings.\"#\".maxDeliveryAttempts"));

        AddressSettings applied =
                broker.getActiveMQServer().getAddressSettingsRepository().getMatch(queue);

        assertThat(applied.getMaxDeliveryAttempts()).isEqualTo(fileAttempts);
        assertThat(maxDeliveryAttempts)
                .as("ecopedia.notifications.max-delivery-attempts tiene que coincidir con el broker")
                .isEqualTo(fileAttempts);
        assertThat(applied.getDeadLetterAddress().toString()).isEqualTo(deadLetterAddress());
        assertThat(applied.isAutoCreateDeadLetterResources()).isTrue();
    }

    @Test
    @DisplayName("Un aviso que falla en cada intento termina en la DLQ, con un ERROR en el último intento")
    void failingMessageEndsInTheDeadLetterQueue(CapturedOutput output) throws Exception {
        doThrow(new IllegalStateException("la base no responde"))
                .when(notificationService)
                .dispatch(any());
        String testCase = UUID.randomUUID().toString();
        String eventId = "booking-950-BOOKING_CONFIRMED";
        long before = deadLetterCountNow();

        send(testCase, validEvent(eventId), "booking-event");

        Message dead = awaitDeadLetter(testCase, before);
        assertThat(dead).isInstanceOf(TextMessage.class);
        assertThat(((TextMessage) dead).getText()).contains(eventId);
        verify(notificationService, times(maxDeliveryAttempts)).dispatch(any());
        assertThat(output)
                .contains("El mensaje " + eventId + " falló en el intento 1 de " + maxDeliveryAttempts)
                .contains("El mensaje " + eventId + " falló en el intento " + maxDeliveryAttempts + " de "
                        + maxDeliveryAttempts + " y pasa a la cola de mensajes muertos DLQ." + queue);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "JSON roto       | esto no es JSON                               | booking-event",
                "tipo de aviso desconocido | {\"eventId\":\"x\",\"type\":\"NO_EXISTE\"} | booking-event",
                "_type desconocido | {\"eventId\":\"x\"}                          | otra-cosa"
            })
    @DisplayName("Un mensaje ilegible no llega al servicio y termina en la DLQ, con su registro")
    void unreadableMessageEndsInTheDeadLetterQueue(String name, String body, String type, CapturedOutput output)
            throws Exception {
        String testCase = UUID.randomUUID().toString();
        long before = deadLetterCountNow();

        send(testCase, body, type);

        Message dead = awaitDeadLetter(testCase, before);
        assertThat(((TextMessage) dead).getText()).isEqualTo(body);
        verify(notificationService, never()).dispatch(any());
        assertThat(output).contains("falló en el intento " + maxDeliveryAttempts + " de " + maxDeliveryAttempts);
        assertThat(output).contains("El mensaje ilegible ");
    }

    private void send(String testCase, String body, String type) {
        new JmsTemplate(connectionFactory).send(queue, session -> {
            TextMessage message = session.createTextMessage(body);
            message.setStringProperty("_type", type);
            message.setStringProperty("testCase", testCase);
            return message;
        });
    }

    /**
     * Espera el mensaje en la cola de mensajes muertos de este destino y lo lee.
     *
     * <p><b>Primero mira del lado del servidor, sin consumir.</b> Si el test se pusiera a leer
     * {@code DLA::DLQ.notifications.dispatch} antes de que el mensaje llegara, Artemis crearía esa
     * cola en el acto, como una cola común, y el mensaje llegaría por un camino que en el broker de
     * Docker no existe. Pasó con la primera versión de este test.
     *
     * <p><b>Y comprueba que no quedó una segunda copia en la {@code DLQ} común.</b> El broker de
     * Docker y el embebido traen una dirección {@code DLQ} con una cola común; si la regla usara esa
     * dirección, el mensaje quedaría en las dos colas, y reprocesar una dejaría la otra colgada (se
     * vio en vivo el 02/11). Por eso la dirección es {@code DLA}.
     */
    private Message awaitDeadLetter(String testCase, long before) throws InterruptedException {
        SimpleString name = SimpleString.of(DEAD_LETTER_PREFIX + queue);
        long genericBefore = deadLetterCount(GENERIC_DLQ);
        long deadline = System.currentTimeMillis() + 15_000;
        while (deadLetterCount(name) <= before && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        Queue dlq = broker.getActiveMQServer().locateQueue(name);
        assertThat(dlq).as("Artemis tendría que haber creado " + name).isNotNull();
        assertThat(dlq.getAddress().toString()).isEqualTo(deadLetterAddress());
        assertThat(deadLetterCount(name))
                .as("el mensaje tendría que haber llegado a " + name)
                .isGreaterThan(before);
        assertThat(deadLetterCount(GENERIC_DLQ))
                .as("el mensaje no tiene que quedar duplicado en la DLQ común")
                .isEqualTo(genericBefore);

        JmsTemplate reader = new JmsTemplate(connectionFactory);
        reader.setReceiveTimeout(5_000);
        Message dead = reader.receiveSelected(deadLetterAddress() + "::" + name, "testCase = '" + testCase + "'");
        assertThat(dead).as("el mensaje de este caso, en " + name).isNotNull();
        return dead;
    }

    private long deadLetterCount(SimpleString name) {
        Queue dlq = broker.getActiveMQServer().locateQueue(name);
        return dlq == null ? 0 : dlq.getMessageCount();
    }

    private long deadLetterCountNow() {
        return deadLetterCount(SimpleString.of(DEAD_LETTER_PREFIX + queue));
    }

    private static String deadLetterAddress() {
        return brokerFile().getProperty("addressSettings.\"#\".deadLetterAddress");
    }

    private static Properties brokerFile() {
        Properties file = new Properties();
        try (Reader reader = Files.newBufferedReader(EmbeddedBrokerSettings.BROKER_PROPERTIES)) {
            file.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private static String validEvent(String eventId) {
        return """
                {"eventId":"%s","type":"BOOKING_CONFIRMED","bookingId":950,"driverId":850,"connectorId":12,\
                "windowStart":"2026-09-30T13:00:00Z","windowEnd":"2026-09-30T14:00:00Z",\
                "occurredAt":"2026-09-28T15:00:00Z"}"""
                .formatted(eventId);
    }
}
