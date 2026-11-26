package com.ecopedia.async.stats.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * El tablero de Estadísticas (ECO-301, RF16, RF19), armado desde las franjas de 15 minutos.
 *
 * <p>La <b>demanda</b> de una franja es la potencia media en esos 15 minutos: los kWh entregados
 * divididos por un cuarto de hora. Sumada entre conectores es la potencia simultánea, y el
 * <b>pico de potencia simultánea</b> es la franja de demanda más alta.
 *
 * @param scope {@code NETWORK} para el administrador, {@code OPERATOR} para un CPO.
 * @param from comienzo del rango, alineado a franja.
 * @param to fin del rango (exclusivo).
 */
public record AnalyticsDashboard(
        String scope,
        Instant from,
        Instant to,
        int slotMinutes,
        Totals totals,
        List<StationDemand> stations,
        List<ConnectorDelivery> connectors) {

    public AnalyticsDashboard {
        stations = List.copyOf(stations);
        connectors = List.copyOf(connectors);
    }

    /** Lo del alcance completo: toda la red o todas las estaciones del operador. */
    public record Totals(
            BigDecimal energyKwh, long sessions, BigDecimal peakSimultaneousPowerKw, Instant peakSimultaneousAt) {}

    /** Demanda de una estación: sus franjas con consumo y su pico de potencia simultánea. */
    public record StationDemand(
            Long stationId,
            String stationName,
            BigDecimal energyKwh,
            long sessions,
            BigDecimal peakSimultaneousPowerKw,
            Instant peakSimultaneousAt,
            List<SlotDemand> slots) {

        public StationDemand {
            slots = List.copyOf(slots);
        }
    }

    /** Una franja de 15 minutos de una estación. Solo aparecen las franjas con lecturas. */
    public record SlotDemand(Instant start, BigDecimal energyKwh, BigDecimal demandKw, long sessionsStarted) {}

    /**
     * Potencia entregada por un conector en el rango.
     *
     * @param peakPowerKw la potencia más alta que informó una lectura.
     * @param peakDemandKw la franja de 15 minutos de mayor potencia media.
     * @param slots sus franjas con lecturas: con ellas el tablero suma solo los conectores elegidos.
     */
    public record ConnectorDelivery(
            Long connectorId,
            Long stationId,
            String stationName,
            String connectorType,
            BigDecimal energyKwh,
            long sessions,
            BigDecimal peakPowerKw,
            BigDecimal peakDemandKw,
            List<SlotDemand> slots) {

        public ConnectorDelivery {
            slots = List.copyOf(slots);
        }
    }
}
