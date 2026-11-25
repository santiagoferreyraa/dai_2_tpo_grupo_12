package com.ecopedia.core.terminal.web.dto;

import com.ecopedia.core.terminal.domain.ConnectorType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Tipo y potencia máxima de un conector, para el alta y para la reconfiguración.
 *
 * <p>La potencia tiene que ser positiva: un conector en 0 kW o en negativo no lo
 * devuelve nunca ningún filtro de potencia de la búsqueda, así que queda invisible
 * para el conductor sin que nadie se entere. El formulario del ABM ya lo rechazaba;
 * acá se cierra también para quien llame a la API directamente.
 *
 * <p>El tope depende del tipo: el que permite su norma ({@link ConnectorType#maxPowerKw()}). Sin
 * tope, una potencia más grande que la columna daba 500 al guardar (lo encontró Schemathesis,
 * QA-25). El contrato publica el más alto de los tres, el de CCS2.
 */
@PowerWithinConnectorStandard
public record ConfigureConnectorRequest(
        @NotNull(message = "El tipo de conector es obligatorio") ConnectorType connectorType,
        @NotNull(message = "La potencia máxima es obligatoria")
                @Positive(message = "La potencia máxima debe ser mayor que cero")
                @Schema(
                        maximum = ConnectorType.HIGHEST_MAX_POWER_KW,
                        description = "En kW. Hasta 500 en CCS2, 400 en CHAdeMO y 43 en Tipo 2.")
                BigDecimal maxPowerKw) {}
