package com.ecopedia.simulation.charger.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El reloj de la telemetría: cada {@code reading-interval} reales, un paso de la simulación.
 *
 * <p><b>El tiempo va acelerado.</b> Con los valores por defecto, cada 2 segundos reales cargan 1
 * minuto simulado, así que una carga de 40 minutos dura un minuto y medio y entra en una demo. Los
 * dos valores se cambian en el .env. Está aparte del simulador para que los tests den los pasos a
 * mano, sin esperar.
 */
@Component
public class TelemetryClock {

    private final ChargerSimulator simulator;

    public TelemetryClock(ChargerSimulator simulator) {
        this.simulator = simulator;
    }

    @Scheduled(
            fixedRateString = "${simulation.charger.reading-interval}",
            initialDelayString = "${simulation.charger.reading-interval}")
    public void tick() {
        simulator.tick();
    }
}
