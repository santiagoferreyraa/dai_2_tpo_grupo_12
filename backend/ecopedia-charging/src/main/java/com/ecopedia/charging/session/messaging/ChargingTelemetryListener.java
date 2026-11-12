package com.ecopedia.charging.session.messaging;

import com.ecopedia.charging.session.domain.ChargingSession;
import com.ecopedia.charging.session.service.ChargingActivationService;
import com.ecopedia.charging.session.service.ChargingTransactionalCoordinator;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

/**
 * Suscriptor al tópico {@code telemetry.charging} de Artemis (RF11, RF14, RF16, ECO-45, ECO-55).
 *
 * <p>Actualiza de forma continua el consumo acumulado ({@code kwhDelivered}) en la sesión persistida
 * y evalúa si se alcanzó el porcentaje objetivo de batería configurado por el conductor para
 * disparar el corte automático (ECO-45).
 */
@Component
public class ChargingTelemetryListener {

    private static final Logger log = LoggerFactory.getLogger(ChargingTelemetryListener.class);

    private final ChargingTransactionalCoordinator coordinator;
    private final ChargingActivationService activationService;

    public ChargingTelemetryListener(
            ChargingTransactionalCoordinator coordinator, ChargingActivationService activationService) {
        this.coordinator = coordinator;
        this.activationService = activationService;
    }

    @JmsListener(
            destination = "${ecopedia.charging.telemetry-topic:telemetry.charging}",
            containerFactory = "topicJmsListenerContainerFactory")
    public void onTelemetry(ChargingTelemetryMessage message) {
        if (message == null || message.sessionId() == null) {
            return;
        }

        try {
            Optional<ChargingSession> updatedSession =
                    coordinator.updateTelemetryProgress(message.sessionId(), message.energyKwh());

            if (updatedSession.isPresent()) {
                ChargingSession session = updatedSession.get();
                if (session.isActive()
                        && session.getTargetBatteryPercent() > 0
                        && message.batteryPercent() != null
                        && message.batteryPercent().compareTo(BigDecimal.valueOf(session.getTargetBatteryPercent()))
                                >= 0) {
                    log.info(
                            "Sesión {} alcanzó porcentaje objetivo {}% (batería={}%): disparando corte automático",
                            session.getSessionId(), session.getTargetBatteryPercent(), message.batteryPercent());
                    activationService.stopSessionByTargetBattery(session.getSessionId());
                }
            }
        } catch (RuntimeException failure) {
            log.warn(
                    "Error al procesar lectura de telemetría para sesión {}: {}",
                    message.sessionId(),
                    failure.getMessage());
        }
    }
}
