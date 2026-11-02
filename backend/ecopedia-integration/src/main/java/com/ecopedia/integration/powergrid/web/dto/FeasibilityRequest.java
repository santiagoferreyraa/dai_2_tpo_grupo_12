package com.ecopedia.integration.powergrid.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * El pedido de SesionesDeCarga: el nodo de la estación y la potencia que se quiere entregar.
 *
 * <p>La potencia no se acota acá: si es inválida, la distribuidora contesta {@code InvalidDemandFault}
 * y vuelve como 422 {@code INVALID_DEMAND}, que es el contrato que hay que respetar.
 */
public record FeasibilityRequest(@NotBlank @Size(max = 40) String nodeId, @NotNull BigDecimal requestedPowerKw) {}
