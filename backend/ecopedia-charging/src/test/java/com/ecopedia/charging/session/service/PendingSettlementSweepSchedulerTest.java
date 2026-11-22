package com.ecopedia.charging.session.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecopedia.charging.session.domain.ChargingSessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PendingSettlementSweepSchedulerTest {

    private final ChargingSessionService sessionService = mock(ChargingSessionService.class);
    private final PendingSettlementSweepScheduler scheduler = new PendingSettlementSweepScheduler(sessionService);

    @Test
    @DisplayName("El barrido invoca retryPendingSettlements en ChargingSessionService")
    void sweep_callsRetryPendingSettlements() {
        when(sessionService.retryPendingSettlements()).thenReturn(2);

        scheduler.sweep();

        verify(sessionService).retryPendingSettlements();
    }

    @Test
    @DisplayName("Fallas durante el barrido son capturadas sin abortar la ejecución programada")
    void sweep_catchesExceptions() {
        when(sessionService.retryPendingSettlements()).thenThrow(new RuntimeException("Broker caído"));

        scheduler.sweep();

        verify(sessionService).retryPendingSettlements();
    }
}
