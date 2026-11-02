package com.ecopedia.charging.checkout.domain;

/**
 * Excepción lanzada cuando no se pudo consultar a Pagos si el conductor tiene un medio de pago (RF02).
 *
 * <p>No es lo mismo que {@link PaymentMethodRequiredException}: ahí Pagos contestó que no hay tarjeta; acá
 * no contestó. La reserva se rechaza igual —la tarjeta es obligatoria para toda transacción—, pero el
 * conductor no tiene nada que corregir: le alcanza con reintentar en un momento.
 */
public class PaymentMethodsUnavailableException extends RuntimeException {

    public PaymentMethodsUnavailableException(Throwable cause) {
        super("No se pudo verificar el medio de pago del conductor", cause);
    }
}
