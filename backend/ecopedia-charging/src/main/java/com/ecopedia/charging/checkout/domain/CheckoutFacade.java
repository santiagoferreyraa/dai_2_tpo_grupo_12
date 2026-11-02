package com.ecopedia.charging.checkout.domain;

/**
 * Interfaz del Patrón de Diseño GoF Facade (Fachada) para el proceso de checkout (ECO-35).
 *
 * <p>Provee una interfaz unificada y de alto nivel que simplifica y coordina la interacción
 * con los subsistemas de Reservas, Tarificación, Pagos y Notificaciones.
 */
public interface CheckoutFacade {

    /**
     * Procesa la confirmación integral de una reserva (checkout).
     *
     * <p>Oculta al cliente la orquestación de:
     * <ol>
     *   <li>Reconocimiento de un pedido repetido por su {@code Idempotency-Key} (QA-29).</li>
     *   <li>Verificación de consentimiento de tolerancia de gracia (RF09).</li>
     *   <li>Consulta de retención temporal vigente y slot no solapado (RF08).</li>
     *   <li>Validación del medio de pago del conductor (RF02).</li>
     *   <li>Cálculo de la seña requerida según el esquema tarifario activo (RF06).</li>
     *   <li>Cobro de la seña en Pagos (RF14, ECO-53).</li>
     *   <li>Confirmación y persistencia de la reserva, con devolución de la seña si falla.</li>
     *   <li>Publicación de evento asincrónico a la cola de mensajería (RF17).</li>
     * </ol>
     *
     * @param driverId Identificador del conductor autenticado
     * @param request Datos del checkout (retención, medio de pago, consentimiento)
     * @param checkoutKey La {@code Idempotency-Key} del pedido: la misma en cada reintento
     * @param authToken Token JWT del conductor para llamadas inter-servicio
     * @return Resultado del checkout con la reserva confirmada y detalles de la seña
     * @throws IdempotencyKeyRequiredException si falta la clave o no tiene la forma esperada
     * @throws IdempotencyKeyReusedException si la clave ya confirmó otra retención
     * @throws GracePeriodNotAcceptedException si no se aceptó la tolerancia de 15 minutos
     * @throws PaymentMethodRequiredException si el conductor no posee medio de pago válido
     * @throws PricingUnavailableException si Tarificación no contestó
     * @throws CardDeclinedException si la pasarela rechazó el cobro de la seña
     * @throws PaymentMethodInvalidException si la tarjeta elegida no sirve para cobrar
     * @throws DepositPaymentUnavailableException si Pagos no contestó al cobrar
     */
    CheckoutResult processBookingCheckout(
            Long driverId, CheckoutBookingRequest request, String checkoutKey, String authToken);
}
