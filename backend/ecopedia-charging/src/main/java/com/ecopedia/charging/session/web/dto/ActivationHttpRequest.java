package com.ecopedia.charging.session.web.dto;

import com.ecopedia.charging.session.domain.ActivationRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ActivationHttpRequest(
        @NotNull(message = "connectorId es obligatorio") Long connectorId,
        Long bookingId,
        @Min(value = 1, message = "El porcentaje objetivo debe ser al menos 1%")
                @Max(value = 100, message = "El porcentaje objetivo no puede superar el 100%")
                Integer targetBatteryPercent,
        String pinOrQr) {

    public ActivationRequest toDomain(Long driverId) {
        int target = targetBatteryPercent != null ? targetBatteryPercent : 80;
        return new ActivationRequest(connectorId, driverId, bookingId, target, pinOrQr);
    }
}
