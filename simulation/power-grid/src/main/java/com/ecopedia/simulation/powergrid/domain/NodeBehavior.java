package com.ecopedia.simulation.powergrid.domain;

/**
 * Cómo contesta la distribuidora por un nodo de prueba.
 *
 * <p>Es lo que permite mostrar en la demo cada respuesta a pedido, sin trucos en Ecopedia: la
 * estación conectada al nodo en mantenimiento recibe el "no" de verdad, que viaja por SOAP como
 * viajaría el de una distribuidora real.
 */
public enum NodeBehavior {

    /** Autoriza hasta la capacidad del nodo. */
    NORMAL,

    /** Hay un tope de demanda vigente: autoriza solo hasta ese tope, que es la capacidad del nodo. */
    DEMAND_CAP,

    /** Ventana de mantenimiento: no autoriza nada. */
    MAINTENANCE,

    /** Autoriza como {@link #NORMAL}, pero tarda más que el timeout de Ecopedia. */
    SLOW
}
