package com.ecopedia.core.terminal.web.dto;

import com.ecopedia.core.terminal.domain.ConnectorType;
import jakarta.validation.constraints.DecimalMax;
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
 * <p>El tope es lo que entra en la columna ({@code NUMERIC(6, 2)}): más que eso daba 500 al guardar
 * (lo encontró Schemathesis, QA-25).
 */
public record ConfigureConnectorRequest(
        @NotNull(message = "El tipo de conector es obligatorio") ConnectorType connectorType,
        @NotNull(message = "La potencia máxima es obligatoria")
                @Positive(message = "La potencia máxima debe ser mayor que cero")
                @DecimalMax(value = "9999.99", message = "La potencia máxima no puede superar 9999,99 kW")
                BigDecimal maxPowerKw) {}
