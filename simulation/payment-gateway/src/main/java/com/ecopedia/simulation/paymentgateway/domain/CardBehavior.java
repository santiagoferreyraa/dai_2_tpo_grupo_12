package com.ecopedia.simulation.paymentgateway.domain;

/**
 * Qué hace la pasarela cuando se le cobra a una tarjeta de prueba.
 *
 * <p>Es lo que permite mostrar en la demo una falla a pedido, sin trucos en Ecopedia: el que paga
 * con la tarjeta que rechaza ve el rechazo de verdad, que viaja por HTTP como viajaría el de una
 * pasarela real. Se decide al tokenizar, mirando el número, y queda guardado junto al token; el
 * número no.
 */
public enum CardBehavior {

    /** Cobra y valida sin problemas. */
    APPROVE,

    /** Rechaza el cobro y también la validación: la tarjeta no es cobrable. */
    DECLINE,

    /**
     * La tarjeta es válida —la validación da que sí— pero el cobro se rechaza por fondos.
     *
     * <p>Es el caso que el postpago tiene que poder mostrar (RNF06): el cargador se habilitó con la
     * tarjeta validada, la energía se entregó, y el cobro final no entra.
     */
    INSUFFICIENT_FUNDS,

    /** Aprueba, pero tarda: sirve para mostrar qué hace Ecopedia cuando la pasarela no contesta a tiempo. */
    SLOW
}
