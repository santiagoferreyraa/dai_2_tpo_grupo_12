package com.ecopedia.async.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.ecopedia.async.config.JmsConfig;
import com.ecopedia.async.stats.domain.DemandSlot;
import com.ecopedia.async.stats.domain.DemandSlotRepository;
import com.ecopedia.async.stats.domain.SessionUsageStat;
import com.ecopedia.async.stats.domain.SessionUsageStatRepository;
import com.ecopedia.async.stats.domain.StationOwnershipInfo;
import com.ecopedia.async.stats.domain.StatsStationDirectory;
import com.ecopedia.async.stats.domain.StatsTelemetryReading;
import com.ecopedia.async.stats.domain.UsageSessionStatus;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Prueba de integración del segundo suscriptor sobre el tópico {@code telemetry.charging} con el
 * broker Artemis embebido (ECO-301, RNF05, QA-28).
 */
@SpringBootTest
@ActiveProfiles("dev")
class StatsTelemetryFlowTest {

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private EmbeddedActiveMQ broker;

    @Autowired
    private SessionUsageStatRepository repository;

    @Autowired
    private DemandSlotRepository slots;

    @MockitoBean
    private StatsStationDirectory stationDirectory;

    @Value("${ecopedia.telemetry.topic}")
    private String topic;

    private JmsTemplate topicJmsTemplate;

    @BeforeEach
    void setUp() {
        topicJmsTemplate = new JmsTemplate(connectionFactory);
        topicJmsTemplate.setPubSubDomain(true);
        when(stationDirectory.findByConnector(anyLong()))
                .thenReturn(Optional.of(new StationOwnershipInfo(1L, "Estación Obelisco", 7L, "CCS2")));
    }

    @Test
    @DisplayName("Consume READING -> duplicado -> FINISHED sin duplicar kWh en session_usage_stats")
    void consumesAndDeduplicatesTelemetryEvents() throws InterruptedException {
        String sessionId = "stats-flow-" + UUID.randomUUID();
        String evt1 = "evt-" + UUID.randomUUID();
        String evt2 = "evt-" + UUID.randomUUID();

        publishTelemetry(evt1, "READING", sessionId, 1L, "2026-11-25T13:00:00Z", "40.00", "5.500", "35.00", null);
        // Mismo eventId duplicado
        publishTelemetry(evt1, "READING", sessionId, 1L, "2026-11-25T13:00:00Z", "40.00", "5.500", "35.00", null);
        publishTelemetry(evt2, "FINISHED", sessionId, 1L, "2026-11-25T13:10:00Z", "0.00", "14.200", "80.00", "STOPPED");

        SessionUsageStat stat = awaitFinished(sessionId);

        assertThat(stat.getStatus()).isEqualTo(UsageSessionStatus.FINISHED);
        assertThat(stat.getEndReason()).isEqualTo(StatsTelemetryReading.EndReason.STOPPED);
        assertThat(stat.getEnergyKwh()).isEqualByComparingTo("14.200");
        assertThat(stat.getPeakPowerKw()).isEqualByComparingTo("40.00");
        assertThat(stat.getReadingsCount()).isEqualTo(2);
        assertThat(stat.getStationName()).isEqualTo("Estación Obelisco");
        assertThat(stat.getStationOwnerId()).isEqualTo(7L);

        DemandSlot slot = slots.findByConnectorIdAndSlotStart(1L, Instant.parse("2026-11-25T13:00:00Z"))
                .orElseThrow();
        assertThat(slot.getEnergyKwh()).isEqualByComparingTo("14.200");
        assertThat(slot.getSessionsStarted()).isEqualTo(1);
        assertThat(slot.getStationOwnerId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Una sesión que cruza dos franjas reparte sus kWh entre las dos")
    void splitsEnergyBetweenSlots() throws InterruptedException {
        String sessionId = "stats-slots-" + UUID.randomUUID();
        long connectorId = 31L;

        publishTelemetry(
                "evt-" + UUID.randomUUID(),
                "READING",
                sessionId,
                connectorId,
                "2026-11-25T15:10:00Z",
                "30.00",
                "0.000",
                "20.00",
                null);
        publishTelemetry(
                "evt-" + UUID.randomUUID(),
                "READING",
                sessionId,
                connectorId,
                "2026-11-25T15:14:00Z",
                "30.00",
                "2.000",
                "25.00",
                null);
        publishTelemetry(
                "evt-" + UUID.randomUUID(),
                "FINISHED",
                sessionId,
                connectorId,
                "2026-11-25T15:22:00Z",
                "0.00",
                "6.000",
                "40.00",
                "STOPPED");

        awaitFinished(sessionId);

        assertThat(slots.findByConnectorIdAndSlotStart(connectorId, Instant.parse("2026-11-25T15:00:00Z"))
                        .orElseThrow()
                        .getEnergyKwh())
                .isEqualByComparingTo("2.000");
        assertThat(slots.findByConnectorIdAndSlotStart(connectorId, Instant.parse("2026-11-25T15:15:00Z"))
                        .orElseThrow()
                        .getEnergyKwh())
                .isEqualByComparingTo("4.000");
    }

    @Test
    @DisplayName("Pub/Sub real: dos suscriptores independientes reciben el mismo mensaje del tópico")
    void pubSubDeliversToTwoIndependentSubscribers() throws Exception {
        String sessionId = "pubsub-" + UUID.randomUUID();
        String evtId = "evt-" + UUID.randomUUID();

        try (JMSContext ctx = connectionFactory.createContext()) {
            Topic jmsTopic = ctx.createTopic(topic);
            try (JMSConsumer secondSubscriber = ctx.createSharedDurableConsumer(jmsTopic, "test-second-sub")) {
                publishTelemetry(
                        evtId, "READING", sessionId, 2L, "2026-11-25T13:20:00Z", "50.00", "8.000", "45.00", null);

                String receivedBySecond = secondSubscriber.receiveBody(String.class, 5000);
                assertThat(receivedBySecond).isNotNull().contains(sessionId);
            }
        }

        SessionUsageStat persistedByStats = awaitSession(sessionId);
        assertThat(persistedByStats.getEnergyKwh()).isEqualByComparingTo("8.000");
    }

    @Test
    @DisplayName("Mensaje malformado en telemetry.charging termina en DLQ tras reintentos")
    void malformedMessageEndsUpInDlq() throws InterruptedException {
        String marker = "corrupt-" + UUID.randomUUID();
        long initialDlqCount = dlqMessageCount();

        topicJmsTemplate.send(topic, session -> {
            TextMessage message = session.createTextMessage("{\"bad\":\"" + marker + "\"");
            message.setStringProperty("_type", JmsConfig.TELEMETRY_EVENT_TYPE);
            return message;
        });

        long deadline = System.currentTimeMillis() + 8000L;
        while (System.currentTimeMillis() < deadline && dlqMessageCount() <= initialDlqCount) {
            Thread.sleep(50L);
        }

        assertThat(dlqMessageCount()).isGreaterThan(initialDlqCount);
    }

    private void publishTelemetry(
            String eventId,
            String type,
            String sessionId,
            long connectorId,
            String timestamp,
            String powerKw,
            String energyKwh,
            String batteryPercent,
            String endReason) {
        String endReasonJson = endReason == null ? "null" : "\"" + endReason + "\"";
        String json =
                """
                {
                  "eventId": "%s",
                  "type": "%s",
                  "sessionId": "%s",
                  "connectorId": %d,
                  "timestamp": "%s",
                  "powerKw": %s,
                  "energyKwh": %s,
                  "batteryPercent": %s,
                  "endReason": %s
                }
                """
                        .formatted(
                                eventId,
                                type,
                                sessionId,
                                connectorId,
                                timestamp,
                                powerKw,
                                energyKwh,
                                batteryPercent,
                                endReasonJson);

        topicJmsTemplate.send(topic, session -> {
            TextMessage message = session.createTextMessage(json);
            message.setStringProperty("_type", JmsConfig.TELEMETRY_EVENT_TYPE);
            return message;
        });
    }

    private SessionUsageStat awaitFinished(String sessionId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8000L;
        while (System.currentTimeMillis() < deadline) {
            Optional<SessionUsageStat> stat = repository.findBySessionId(sessionId);
            if (stat.isPresent() && stat.get().getStatus() == UsageSessionStatus.FINISHED) {
                return stat.get();
            }
            Thread.sleep(50L);
        }
        return repository.findBySessionId(sessionId).orElseThrow();
    }

    private SessionUsageStat awaitSession(String sessionId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8000L;
        while (System.currentTimeMillis() < deadline) {
            Optional<SessionUsageStat> stat = repository.findBySessionId(sessionId);
            if (stat.isPresent()) {
                return stat.get();
            }
            Thread.sleep(50L);
        }
        return repository.findBySessionId(sessionId).orElseThrow();
    }

    private long dlqMessageCount() {
        Queue dlq = broker.getActiveMQServer().locateQueue(SimpleString.of("DLQ." + topic));
        if (dlq != null) {
            return dlq.getMessageCount();
        }
        Queue genericDlq = broker.getActiveMQServer().locateQueue(SimpleString.of("DLQ"));
        return genericDlq != null ? genericDlq.getMessageCount() : 0L;
    }
}
