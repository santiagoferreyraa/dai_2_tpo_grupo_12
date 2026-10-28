package com.ecopedia.integration.powergrid.domain;

import java.math.BigDecimal;

/**
 * Lo que contestó la distribuidora sobre un nodo.
 *
 * <p>Un "no" es una respuesta válida, no un error: {@code feasible} en {@code false}, con el motivo
 * y el tope que el nodo sí acepta, que es lo que permitiría cargar a menos potencia.
 *
 * @param maxAllowedPowerKw el tope que el nodo acepta ahora.
 * @param reason por qué no autoriza; {@code null} cuando autoriza.
 */
public record Feasibility(boolean feasible, BigDecimal maxAllowedPowerKw, Restriction reason) {

    /** Por qué la distribuidora no autoriza. */
    public enum Restriction {
        /** Hay un tope de demanda vigente y lo pedido lo supera. */
        DEMAND_CAP_EXCEEDED,
        /** El nodo está en mantenimiento. */
        MAINTENANCE_WINDOW
    }
}
