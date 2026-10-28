package com.ecopedia.integration.powergrid.domain;

import java.math.BigDecimal;

/**
 * RedEléctrica: la regla de factibilidad de la distribuidora para una carga rápida (RF15).
 *
 * <p>Componente de borde, stateless y sin capa de presentación (ARQUITECTURA, ficha 7): lo consume
 * SesionesDeCarga antes de habilitar una carga de alta potencia. Cómo llega SesionesDeCarga, que
 * vive en otro artefacto, se resuelve con ECO-52.
 */
public interface PowerGridService {

    /**
     * ¿El nodo aguanta la potencia pedida?
     *
     * <p>Tres resultados posibles, y cada uno pide algo distinto al que llama: una respuesta (que
     * puede ser "no", con el motivo), un pedido que la distribuidora no puede procesar, o una
     * distribuidora que no contesta.
     *
     * @throws PowerGridRequestRejectedException si la distribuidora rechazó el pedido: nodo
     *     desconocido, potencia inválida o un pedido que no cumple el contrato
     * @throws PowerGridUnavailableException si la distribuidora no contestó a tiempo
     */
    Feasibility checkFeasibility(String nodeId, BigDecimal requestedPowerKw);
}
