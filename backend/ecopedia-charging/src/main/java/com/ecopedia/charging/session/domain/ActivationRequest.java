package com.ecopedia.charging.session.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ActivationRequest(
        @NotNull(message = "connectorId es obligatorio") Long connectorId,
        @NotNull(message = "driverId es obligatorio") Long driverId,
        Long bookingId,
        @Min(value = 1, message = "El porcentaje objetivo debe ser al menos 1%")
                @Max(value = 100, message = "El porcentaje objetivo no puede superar el 100%")
                int targetBatteryPercent,
        String pinOrQr) {}
