package com.ecopedia.charging.session.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Lo que pide el conductor frente a la terminal (RF11, ECO-44).
 *
 * @param connectorId opcional: el conector lo dice el código. Si viene, tiene que ser el mismo que
 *     abre el código; sirve para que el front que ya sabe dónde está el conductor —la reserva que
 *     eligió, la estación del mapa— detecte que escaneó la terminal de al lado.
 * @param bookingId opcional: sin reserva, la habilitación busca si el conductor tiene una confirmada
 *     para ese conector y en hora; si no tiene, la carga es sin reserva (RF10).
 * @param pinOrQr el PIN de la terminal o el contenido de su QR. Es lo que acredita la presencia.
 */
public record ActivationRequest(
        Long connectorId,
        @NotNull(message = "driverId es obligatorio") Long driverId,
        Long bookingId,
        @Min(value = 1, message = "El porcentaje objetivo debe ser al menos 1%")
                @Max(value = 100, message = "El porcentaje objetivo no puede superar el 100%")
                int targetBatteryPercent,
        String pinOrQr) {}
