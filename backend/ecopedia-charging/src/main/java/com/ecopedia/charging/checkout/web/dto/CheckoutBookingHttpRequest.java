package com.ecopedia.charging.checkout.web.dto;

import com.ecopedia.charging.checkout.domain.CheckoutBookingRequest;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo HTTP recibido para iniciar el checkout de una reserva (ECO-35).
 */
public record CheckoutBookingHttpRequest(
        @NotNull(message = "El id de la retención es obligatorio") UUID holdId,
        Long paymentMethodId,
        Boolean acceptGracePeriod) {

    public CheckoutBookingRequest toDomain() {
        return new CheckoutBookingRequest(holdId, paymentMethodId, Boolean.TRUE.equals(acceptGracePeriod));
    }
}
