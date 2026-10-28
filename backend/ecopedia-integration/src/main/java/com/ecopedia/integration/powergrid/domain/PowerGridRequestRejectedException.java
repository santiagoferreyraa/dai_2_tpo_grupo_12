package com.ecopedia.integration.powergrid.domain;

/**
 * La distribuidora contestó, pero con un Fault: el pedido no se pudo procesar.
 *
 * <p>No es un "no" de la red —eso es un {@link Feasibility} con {@code feasible = false}— ni una
 * caída. Es un pedido que no tiene sentido para la distribuidora, y el {@link #getReason() motivo}
 * dice cuál, para que quien llama decida sin leer el texto. El mensaje es para el log.
 */
public class PowerGridRequestRejectedException extends RuntimeException {

    /** Qué rechazó la distribuidora. */
    public enum Reason {
        /** El nodo no existe en la distribuidora ({@code UnknownNodeFault}). */
        UNKNOWN_NODE,
        /** La potencia es nula, negativa o más de lo que maneja un nodo ({@code InvalidDemandFault}). */
        INVALID_DEMAND,
        /** El pedido no cumple el XSD. Si pasa, es un error nuestro: el nodo se valida en core. */
        INVALID_REQUEST
    }

    private final Reason reason;

    public PowerGridRequestRejectedException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
