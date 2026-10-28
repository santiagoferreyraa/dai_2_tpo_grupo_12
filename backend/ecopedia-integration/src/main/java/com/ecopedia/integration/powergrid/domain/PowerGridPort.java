package com.ecopedia.integration.powergrid.domain;

import java.math.BigDecimal;

/**
 * Puerto de salida hacia la distribuidora.
 *
 * <p>Es el Adapter del lado del legado, el mismo patrón que {@code PaymentGatewayPort} del lado del
 * partner moderno: RedEléctrica habla con este contrato y nunca con SOAP. Lo implementa
 * {@code PowerGridWsClient} contra la distribuidora simulada, que corre como proceso aparte
 * (RNF04). Que una integración sea SOAP y la otra REST queda adentro de cada adaptador.
 */
public interface PowerGridPort {

    /**
     * @throws PowerGridRequestRejectedException si la distribuidora devolvió un Fault del contrato
     * @throws PowerGridUnavailableException si no contestó, o contestó algo que no es una respuesta
     */
    Feasibility checkFeasibility(String nodeId, BigDecimal requestedPowerKw);
}
