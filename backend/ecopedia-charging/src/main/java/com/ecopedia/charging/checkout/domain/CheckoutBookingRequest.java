package com.ecopedia.charging.checkout.domain;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Datos requeridos por el Facade de Checkout para procesar la confirmación de una reserva (ECO-35).
 *
 * @param holdId Identificador único de la retención temporal del slot (RF08)
 * @param paymentMethodId Identificador opcional del medio de pago seleccionado
 * @param acceptGracePeriod Aceptación explícita de la tolerancia de 15 minutos (RF09)
 */
public record CheckoutBookingRequest(
        @NotNull(message = "El id de la retención es obligatorio") UUID holdId,
        Long paymentMethodId,
        boolean acceptGracePeriod) {}
