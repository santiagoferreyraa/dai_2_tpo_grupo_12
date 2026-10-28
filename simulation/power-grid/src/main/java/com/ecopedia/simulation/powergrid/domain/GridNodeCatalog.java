package com.ecopedia.simulation.powergrid.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Los nodos que conoce la distribuidora: un catálogo fijo, uno por comportamiento.
 *
 * <p>Las 15 estaciones del seed de Ecopedia están repartidas entre estos nodos (ver la migración
 * {@code add_stations_node_id} de core), así que cada respuesta se puede mostrar desde el mapa. La
 * lista para la demo está en el README.
 */
public final class GridNodeCatalog {

    /** Lo máximo que se le puede pedir a un nodo. Más que esto es un {@code InvalidDemandFault}. */
    public static final BigDecimal MAX_SUPPORTED_POWER_KW = new BigDecimal("1000.00");

    /**
     * Un nodo de prueba.
     *
     * @param capacityKw lo que el nodo puede entregar hoy; en el de tope de demanda, el tope.
     */
    public record GridNode(String id, NodeBehavior behavior, BigDecimal capacityKw) {}

    private static final Map<String, GridNode> NODES = Map.of(
            "CABA-CENTRO", node("CABA-CENTRO", NodeBehavior.NORMAL, "600.00"),
            "CABA-NORTE", node("CABA-NORTE", NodeBehavior.NORMAL, "400.00"),
            "CABA-OESTE", node("CABA-OESTE", NodeBehavior.DEMAND_CAP, "50.00"),
            "CABA-SUR", node("CABA-SUR", NodeBehavior.MAINTENANCE, "0.00"),
            "GBA-NORTE", node("GBA-NORTE", NodeBehavior.NORMAL, "300.00"),
            "LA-PLATA", node("LA-PLATA", NodeBehavior.SLOW, "350.00"));

    private GridNodeCatalog() {}

    private static GridNode node(String id, NodeBehavior behavior, String capacityKw) {
        return new GridNode(id, behavior, new BigDecimal(capacityKw));
    }

    public static Optional<GridNode> find(String nodeId) {
        return Optional.ofNullable(NODES.get(nodeId));
    }
}
