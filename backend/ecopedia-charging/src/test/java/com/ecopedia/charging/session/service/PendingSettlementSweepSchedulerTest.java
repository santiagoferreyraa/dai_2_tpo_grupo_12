package com.ecopedia.charging.session.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PendingSettlementSweepSchedulerTest {

    private final ChargingActivationService activationService = mock(ChargingActivationService.class);
    private final PendingSettlementSweepScheduler scheduler = new PendingSettlementSweepScheduler(activationService);

    @Test
    @DisplayName("El barrido invoca retryPendingSettlements en ChargingActivationService")
    void sweep_callsRetryPendingSettlements() {
        when(activationService.retryPendingSettlements()).thenReturn(2);

        scheduler.sweep();

        verify(activationService).retryPendingSettlements();
    }

    @Test
    @DisplayName("Fallas durante el barrido son capturadas sin abortar la ejecución programada")
    void sweep_catchesExceptions() {
        when(activationService.retryPendingSettlements()).thenThrow(new RuntimeException("Broker caído"));

        scheduler.sweep();

        verify(activationService).retryPendingSettlements();
    }
}
