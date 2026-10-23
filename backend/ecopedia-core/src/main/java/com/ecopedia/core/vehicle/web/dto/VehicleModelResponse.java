package com.ecopedia.core.vehicle.web.dto;

import com.ecopedia.core.terminal.domain.ConnectorType;
import com.ecopedia.core.vehicle.domain.VehicleModel;
import java.math.BigDecimal;

/**
 * Un modelo del catálogo tal como viaja en el JSON.
 *
 * <p>Va la ficha entera y no solo marca, modelo e id. La pantalla no usa estos números
 * únicamente para dibujar el selector: con el auto ya elegido tiene que contar cuántas
 * estaciones lo cargan y estimar cuánto tarda, y hacerlo con una segunda llamada por cada número
 * sería pedir de a uno lo que la misma fila ya trae.
 *
 * <p>No lleva {@code active}: el catálogo que sale por acá ya viene filtrado, así que la
 * propiedad sería {@code true} en todas las filas y no le diría nada a nadie.
 */
public record VehicleModelResponse(
        Long id,
        String brand,
        String name,
        ConnectorType connectorType,
        BigDecimal motorKw,
        BigDecimal maxChargeKw,
        BigDecimal batteryKwh,
        String imageSlug) {

    public static VehicleModelResponse fromDomain(VehicleModel model) {
        return new VehicleModelResponse(
                model.getId(),
                model.getBrand(),
                model.getName(),
                model.getConnectorType(),
                model.getMotorKw(),
                model.getMaxChargeKw(),
                model.getBatteryKwh(),
                model.getImageSlug());
    }
}
