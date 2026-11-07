package com.ecopedia.charging.checkout.domain;

/**
 * Puerto secundario hacia el componente de Pagos (ecopedia-integration).
 *
 * <p>Permite al Facade de Checkout verificar las precondiciones de medios de pago del conductor (RF02).
 */
public interface PaymentDirectory {

    /**
     * Verifica si el conductor tiene al menos un medio de pago válido y utilizable para operar.
     *
     * @param driverId Identificador del conductor
     * @param authToken Token de autenticación del usuario (para propagación si corresponde)
     * @return true si tiene tarjeta registrada y cobrable, false en caso contrario
     * @throws PaymentMethodsUnavailableException si Pagos no contestó: no se sabe, y no saber no habilita
     */
    boolean hasUsablePaymentMethod(Long driverId, String authToken);

    /**
     * Validación completa sin mover dinero (RF11, RF14, ECO-54):
     * Comprueba que la tarjeta sea cobrable ante la pasarela y que el conductor no tenga deuda.
     *
     * @param driverId Identificador del conductor.
     * @param paymentMethodId Identificador opcional de la tarjeta elegida.
     * @throws PaymentMethodInvalidException si la tarjeta fue declinada, no existe o el conductor arrastra deuda.
     * @throws PaymentMethodsUnavailableException si Pagos o la pasarela no responden (503).
     */
    void validatePaymentMethodForCharging(Long driverId, Long paymentMethodId);
}
