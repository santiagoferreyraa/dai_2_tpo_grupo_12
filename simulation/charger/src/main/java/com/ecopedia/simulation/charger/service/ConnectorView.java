package com.ecopedia.simulation.charger.service;

import com.ecopedia.simulation.charger.domain.ChargingSession;
import com.ecopedia.simulation.charger.domain.ConnectorStatus;
import java.math.BigDecimal;

/** Una foto de un conector: en qué está y, si carga, cómo va. */
public record ConnectorView(long connectorId, ConnectorStatus status, SessionView session) {

    public record SessionView(String sessionId, BigDecimal powerKw, BigDecimal energyKwh, BigDecimal batteryPercent) {

        static SessionView of(ChargingSession session) {
            return new SessionView(
                    session.sessionId(), session.powerKw(), session.energyKwh(), session.batteryPercent());
        }
    }
}
