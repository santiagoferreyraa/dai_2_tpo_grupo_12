package com.ecopedia.charging.session.web.dto;

import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Petición opcional al detener una sesión de carga para informar la energía entregada (RF14, ECO-55).
 */
public record StopSessionRequest(
        @PositiveOrZero(message = "kwhDelivered no puede ser negativo") BigDecimal kwhDelivered) {}
