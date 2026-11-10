package com.ecopedia.charging.checkout.domain;

import java.math.BigDecimal;

/**
 * Puerto secundario hacia el componente de Pagos (ecopedia-integration).
 *
 * <p>Permite al Facade de Checkout verificar las precondiciones de medios de pago del conductor (RF02)
 * y a SesionesDeCarga cobrar el consumo adicional al cerrar la sesión (RF14, ECO-55).
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

    /**
     * Cobra el consumo adicional de una sesión finalizada o registra la deuda si el cobro falla (RF14, ECO-55).
     *
     * @param driverId Identificador del conductor
     * @param sessionId Identificador de la sesión de carga cerrada
     * @param amount Saldo adicional positivo a cobrar
     * @return {@link ConsumptionPaymentStatus#CHARGED} o {@link ConsumptionPaymentStatus#DEBT_RECORDED}
     * @throws PaymentMethodsUnavailableException si ecopedia-integration no responde al momento del cierre
     */
    ConsumptionPaymentStatus chargeConsumption(Long driverId, String sessionId, BigDecimal amount);
}
