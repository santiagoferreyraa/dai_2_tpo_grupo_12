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
     */
    boolean hasUsablePaymentMethod(Long driverId, String authToken);
}
