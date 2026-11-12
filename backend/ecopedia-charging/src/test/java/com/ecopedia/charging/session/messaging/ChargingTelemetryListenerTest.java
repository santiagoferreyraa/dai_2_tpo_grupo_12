package com.ecopedia.charging.session.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.service.ChargingActivationService;
import com.ecopedia.charging.session.service.ChargingTransactionalCoordinator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChargingTelemetryListenerTest {

    private final ChargingTransactionalCoordinator coordinator = mock(ChargingTransactionalCoordinator.class);
    private final ChargingActivationService activationService = mock(ChargingActivationService.class);

    private ChargingTelemetryListener listener;

    @BeforeEach
    void setUp() {
        listener = new ChargingTelemetryListener(coordinator, activationService);
    }

    @Test
    @DisplayName("Lectura regular de telemetría actualiza kWh en sesión y no corta si batería < objetivo")
    void onTelemetry_updatesKwhWithoutCuttingOff() {
        Instant now = Instant.now();
        ChargingTelemetryMessage message = new ChargingTelemetryMessage(
                "evt-1",
                ChargingTelemetryMessage.Type.READING,
                "sess-1",
                10L,
                now,
                new BigDecimal("22.00"),
                new BigDecimal("12.500"),
                new BigDecimal("60.0"),
                null);

        ChargingSession session =
                ChargingSession.start("sess-1", 100L, 10L, 5L, 80, new BigDecimal("22.00"), now.minusSeconds(600));

        when(coordinator.updateTelemetryProgress("sess-1", new BigDecimal("12.500")))
                .thenReturn(Optional.of(session));

        listener.onTelemetry(message);

        verify(coordinator).updateTelemetryProgress("sess-1", new BigDecimal("12.500"));
        verify(activationService, never()).stopSessionByTargetBattery(any());
    }

    @Test
    @DisplayName(
            "Cuando la batería alcanza el porcentaje objetivo (batteryPercent >= target), dispara corte automático (ECO-45)")
    void onTelemetry_triggersAutoCutoffWhenTargetBatteryReached() {
        Instant now = Instant.now();
        ChargingTelemetryMessage message = new ChargingTelemetryMessage(
                "evt-2",
                ChargingTelemetryMessage.Type.READING,
                "sess-1",
                10L,
                now,
                new BigDecimal("22.00"),
                new BigDecimal("20.000"),
                new BigDecimal("80.0"),
                null);

        ChargingSession session =
                ChargingSession.start("sess-1", 100L, 10L, 5L, 80, new BigDecimal("22.00"), now.minusSeconds(1200));

        when(coordinator.updateTelemetryProgress("sess-1", new BigDecimal("20.000")))
                .thenReturn(Optional.of(session));

        listener.onTelemetry(message);

        verify(coordinator).updateTelemetryProgress("sess-1", new BigDecimal("20.000"));
        verify(activationService).stopSessionByTargetBattery("sess-1");
    }

    @Test
    @DisplayName("Mensaje con payload nulo o sessionId ausente es ignorado sin fallar")
    void onTelemetry_nullMessageIgnored() {
        listener.onTelemetry(null);
        listener.onTelemetry(new ChargingTelemetryMessage(
                "evt-null",
                ChargingTelemetryMessage.Type.READING,
                null,
                10L,
                Instant.now(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null));

        verify(coordinator, never()).updateTelemetryProgress(any(), any());
        verify(activationService, never()).stopSessionByTargetBattery(any());
    }

    @Test
    @DisplayName("Excepciones en persistencia de telemetría son capturadas sin relanzar al contenedor JMS")
    void onTelemetry_catchesRuntimeExceptions() {
        Instant now = Instant.now();
        ChargingTelemetryMessage message = new ChargingTelemetryMessage(
                "evt-err",
                ChargingTelemetryMessage.Type.READING,
                "sess-err",
                10L,
                now,
                new BigDecimal("22.00"),
                new BigDecimal("5.000"),
                new BigDecimal("40.0"),
                null);

        when(coordinator.updateTelemetryProgress("sess-err", new BigDecimal("5.000")))
                .thenThrow(new RuntimeException("DB down"));

        listener.onTelemetry(message);

        verify(activationService, never()).stopSessionByTargetBattery(any());
    }
}
