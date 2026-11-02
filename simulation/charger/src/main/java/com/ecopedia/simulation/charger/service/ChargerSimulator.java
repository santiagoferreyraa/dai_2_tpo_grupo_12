package com.ecopedia.simulation.charger.service;

import com.ecopedia.simulation.charger.domain.ChargerException;
import com.ecopedia.simulation.charger.domain.ChargingSession;
import com.ecopedia.simulation.charger.domain.ConnectorStatus;
import com.ecopedia.simulation.charger.domain.TelemetryEvent;
import com.ecopedia.simulation.charger.domain.TelemetryEvent.EndReason;
import com.ecopedia.simulation.charger.domain.TelemetryEvent.Type;
import com.ecopedia.simulation.charger.service.ConnectorView.SessionView;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * La flota de cargadores: qué conector está cargando, cuál está roto, y el avance de cada carga.
 *
 * <p><b>Todo en memoria, a propósito.</b> Es el estado del hardware, no datos de Ecopedia: si el
 * simulador se reinicia, las cargas en curso se cortan, como se cortarían si se cortara la luz de la
 * estación. Los conectores no se dan de alta: cualquier id positivo es un conector, porque el
 * simulador no conoce la base de Ecopedia —igual que la central de un fabricante no la conoce—.
 *
 * <p>Los métodos son {@code synchronized}: el tick de la telemetría y las órdenes que llegan por REST
 * tocan los mismos conectores, y con un solo simulador y pocas cargas un candado alcanza.
 */
@Service
public class ChargerSimulator {

    private static final Logger log = LoggerFactory.getLogger(ChargerSimulator.class);

    private final TelemetryPublisher publisher;
    private final Clock clock;
    private final RandomGenerator random;
    private final double minutesPerReading;
    private final BigDecimal defaultBatteryKwh;
    private final int minInitialPercent;
    private final int maxInitialPercent;

    private final Map<Long, ChargingSession> sessions = new HashMap<>();
    private final Set<Long> faulted = new HashSet<>();

    public ChargerSimulator(
            TelemetryPublisher publisher,
            Clock clock,
            RandomGenerator random,
            @Value("${simulation.charger.minutes-per-reading}") double minutesPerReading,
            @Value("${simulation.charger.default-battery-kwh}") BigDecimal defaultBatteryKwh,
            @Value("${simulation.charger.initial-battery-percent.min}") int minInitialPercent,
            @Value("${simulation.charger.initial-battery-percent.max}") int maxInitialPercent) {
        this.publisher = publisher;
        this.clock = clock;
        this.random = random;
        this.minutesPerReading = minutesPerReading;
        this.defaultBatteryKwh = defaultBatteryKwh;
        this.minInitialPercent = minInitialPercent;
        this.maxInitialPercent = maxInitialPercent;
    }

    /** El resultado de una orden de activar: el conector, y si la orden lo encendió o ya estaba. */
    public record Activation(ConnectorView connector, boolean started) {}

    /**
     * Enciende el conector para la sesión.
     *
     * <p><b>Idempotente para la misma sesión:</b> si el conector ya está cargando esa sesión, no
     * hace nada y devuelve cómo va. Así un reintento de Terminales —porque no le llegó la respuesta—
     * no reinicia la carga.
     *
     * <p><b>Sin broker no enciende.</b> La primera lectura sale acá mismo, antes de dar el conector
     * por encendido; si no se puede publicar, la orden se rechaza. Una carga sin lecturas no se puede
     * cortar al llegar al objetivo ni cobrar, y en postpago eso es regalar la energía.
     *
     * @throws ChargerException 409 si el conector está roto u ocupado por otra sesión; 503 si no se
     *     pudo publicar la primera lectura
     */
    public synchronized Activation activate(long connectorId, ChargeOrder order) {
        if (faulted.contains(connectorId)) {
            throw ChargerException.conflict("connector_faulted", "Connector " + connectorId + " is faulted");
        }
        ChargingSession current = sessions.get(connectorId);
        if (current != null) {
            if (current.sessionId().equals(order.sessionId())) {
                return new Activation(viewOf(connectorId), false);
            }
            throw ChargerException.conflict(
                    "connector_busy", "Connector " + connectorId + " is charging another session");
        }

        ChargingSession session = new ChargingSession(
                order.sessionId(),
                connectorId,
                order.powerLimitKw().doubleValue(),
                (order.vehicleMaxChargeKw() != null ? order.vehicleMaxChargeKw() : order.powerLimitKw()).doubleValue(),
                (order.batteryCapacityKwh() != null ? order.batteryCapacityKwh() : defaultBatteryKwh).doubleValue(),
                order.initialBatteryPercent() != null ? order.initialBatteryPercent() : randomInitialPercent());
        try {
            publisher.publish(eventOf(session, Type.READING, null));
        } catch (RuntimeException brokerDown) {
            log.warn("No se activa el conector {}: no se pudo publicar la telemetría", connectorId, brokerDown);
            throw new ChargerException(
                    HttpStatus.SERVICE_UNAVAILABLE, "telemetry_unavailable", "Telemetry broker is not reachable");
        }
        sessions.put(connectorId, session);
        log.info(
                "Conector {} encendido para la sesión {} (batería al {} %)",
                connectorId, session.sessionId(), session.batteryPercent());
        return new Activation(viewOf(connectorId), true);
    }

    /**
     * Apaga el conector y devuelve cómo terminó la sesión. Si no estaba cargando, no hay nada que
     * apagar y vuelve vacío: desactivar dos veces es lo mismo que una.
     */
    public synchronized Optional<SessionView> deactivate(long connectorId) {
        ChargingSession session = sessions.remove(connectorId);
        if (session == null) return Optional.empty();
        publisher.publishOrWarn(eventOf(session, Type.FINISHED, EndReason.STOPPED));
        log.info("Conector {} apagado: sesión {}, {} kWh", connectorId, session.sessionId(), session.energyKwh());
        return Optional.of(SessionView.of(session));
    }

    /**
     * Rompe el conector. Si estaba cargando, la carga se corta en el acto y sale una lectura de
     * falla con lo entregado hasta ahí: es "la máquina rota" de RF13, la que reintegra la seña.
     */
    public synchronized ConnectorView breakConnector(long connectorId) {
        ChargingSession session = sessions.remove(connectorId);
        if (session != null) {
            publisher.publishOrWarn(eventOf(session, Type.FAULT, EndReason.FAULT));
            log.warn("Conector {} roto en plena carga: sesión {} cortada", connectorId, session.sessionId());
        }
        faulted.add(connectorId);
        return viewOf(connectorId);
    }

    public synchronized ConnectorView repair(long connectorId) {
        faulted.remove(connectorId);
        return viewOf(connectorId);
    }

    public synchronized ConnectorView status(long connectorId) {
        return viewOf(connectorId);
    }

    /**
     * Un paso de reloj: cada conector que carga avanza {@code minutes-per-reading} minutos simulados
     * y publica su lectura. El que llena la batería publica la última y queda libre.
     */
    public synchronized void tick() {
        for (ChargingSession session : List.copyOf(sessions.values())) {
            session.advance(minutesPerReading);
            if (session.isFull()) {
                sessions.remove(session.connectorId());
                publisher.publishOrWarn(eventOf(session, Type.FINISHED, EndReason.BATTERY_FULL));
                log.info("Conector {}: batería llena, sesión {} terminada", session.connectorId(), session.sessionId());
            } else {
                publisher.publishOrWarn(eventOf(session, Type.READING, null));
            }
        }
    }

    private ConnectorView viewOf(long connectorId) {
        if (faulted.contains(connectorId)) return new ConnectorView(connectorId, ConnectorStatus.FAULTED, null);
        ChargingSession session = sessions.get(connectorId);
        if (session == null) return new ConnectorView(connectorId, ConnectorStatus.AVAILABLE, null);
        return new ConnectorView(connectorId, ConnectorStatus.CHARGING, SessionView.of(session));
    }

    private TelemetryEvent eventOf(ChargingSession session, Type type, EndReason endReason) {
        return new TelemetryEvent(
                UUID.randomUUID().toString(),
                type,
                session.sessionId(),
                session.connectorId(),
                clock.instant(),
                session.powerKw(),
                session.energyKwh(),
                session.batteryPercent(),
                endReason);
    }

    private int randomInitialPercent() {
        return random.nextInt(minInitialPercent, maxInitialPercent + 1);
    }
}
