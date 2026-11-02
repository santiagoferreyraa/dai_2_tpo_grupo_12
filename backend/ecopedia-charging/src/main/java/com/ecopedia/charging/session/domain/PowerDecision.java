package com.ecopedia.charging.session.domain;

import java.math.BigDecimal;

/**
 * Con qué potencia se habilita un conector, y por qué.
 *
 * <p>El motivo es para quien la usa —la habilitación, con ECO-44—: decide si la carga puede
 * arrancar, si corresponde avisar al conductor que sale con potencia reducida, y qué reintegrar.
 *
 * @param powerKw la potencia con que se enciende el cargador; 0 si no puede arrancar.
 */
public record PowerDecision(BigDecimal powerKw, Outcome outcome) {

    public enum Outcome {
        /** Conector de carga normal (hasta el umbral): no se le pregunta a la distribuidora (RF15). */
        STANDARD_CHARGE,
        /** Carga rápida autorizada por la distribuidora: la potencia del conector. */
        FAST_AUTHORIZED,
        /** La distribuidora dijo que no, pero con un tope: se carga hasta ese tope. */
        LIMITED_BY_GRID,
        /** La distribuidora dijo que no, con tope 0: la carga no puede iniciarse (RF13, reintegro). */
        REJECTED_BY_GRID,
        /** La estación no tiene nodo: no ofrece carga rápida. No es un error. */
        NO_FAST_CHARGE_AT_STATION,
        /** La distribuidora no conoce el nodo de la estación: el dato está mal cargado. */
        UNKNOWN_NODE,
        /** La distribuidora o RedEléctrica no contestaron. */
        GRID_UNAVAILABLE,
        /** La distribuidora rechazó el pedido por algo nuestro (potencia inválida, contrato). */
        GRID_REQUEST_ERROR
    }

    /** {@code false} solo cuando la red no autoriza nada: es la "falla en la habilitación" de RF13. */
    public boolean canStart() {
        return outcome != Outcome.REJECTED_BY_GRID;
    }
}
