package com.ecopedia.charging.session.web.dto;

import com.ecopedia.charging.session.domain.ActivationRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El pedido de habilitación tal como llega del front (RF11, ECO-44).
 *
 * <p>{@code pinOrQr} es lo único obligatorio además del porcentaje: el código de la terminal dice qué
 * conector es. {@code connectorId} y {@code bookingId} son opcionales; ver {@link ActivationRequest}.
 */
public record ActivationHttpRequest(
        Long connectorId,
        Long bookingId,
        @Min(value = 1, message = "El porcentaje objetivo debe ser al menos 1%")
                @Max(value = 100, message = "El porcentaje objetivo no puede superar el 100%")
                Integer targetBatteryPercent,
        @NotBlank(message = "Falta el código de la terminal: escaneá el QR o tipeá el PIN")
                @Size(max = 64, message = "El código de la terminal es demasiado largo")
                String pinOrQr) {

    public ActivationRequest toDomain(Long driverId) {
        int target = targetBatteryPercent != null ? targetBatteryPercent : 80;
        return new ActivationRequest(connectorId, driverId, bookingId, target, pinOrQr);
    }
}
