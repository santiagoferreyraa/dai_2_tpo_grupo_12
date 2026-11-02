package com.ecopedia.core.terminal.domain;

import java.util.Optional;

/**
 * Puerto de salida de Terminales hacia el hardware de carga (ARQUITECTURA §2.3, ficha 2).
 *
 * <p>Es el tercer Adapter, después de {@code PaymentGatewayPort} y {@code PowerGridPort}: Terminales
 * es el dueño del hardware y le da las órdenes por este contrato, nunca por HTTP. Lo implementa
 * {@code ChargerRestClient} contra el simulador de cargadores, que corre como proceso aparte
 * (RNF04). El día que haya cargadores de verdad, que hablan OCPP o la API de cada fabricante, se
 * agrega otro adaptador y el negocio no se entera.
 *
 * <p><b>Solo órdenes.</b> Lo que pasa durante la carga —kWh, potencia, batería— no vuelve por acá:
 * el cargador lo publica en el tópico {@code telemetry.charging}, y lo leen SesionesDeCarga y
 * Estadísticas.
 *
 * <p>Todavía no lo llama nadie: SesionesDeCarga le pide la activación a Terminales con ECO-44.
 */
public interface ChargerPort {

    /**
     * Enciende el conector para la sesión. Repetir la orden con la misma sesión no reinicia la
     * carga: devuelve cómo va.
     *
     * @throws ChargerRejectedException si el conector está roto u ocupado por otra sesión
     * @throws ChargerUnavailableException si el cargador no contestó, o no pudo publicar su telemetría
     */
    ChargerReading activate(ChargingOrder order);

    /**
     * Apaga el conector y devuelve cómo terminó la sesión, o vacío si no estaba cargando: apagar dos
     * veces es lo mismo que una.
     *
     * @throws ChargerUnavailableException si el cargador no contestó
     */
    Optional<ChargerReading> deactivate(long connectorId);
}
