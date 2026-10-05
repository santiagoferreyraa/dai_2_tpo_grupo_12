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
     *   <li>Verificación de consentimiento de tolerancia de gracia (RF09).</li>
     *   <li>Consulta de retención temporal vigente y slot no solapado (RF08).</li>
     *   <li>Cálculo de la seña requerida según el esquema tarifario activo (RF06).</li>
     *   <li>Validación del medio de pago del conductor (RF02).</li>
     *   <li>Confirmación y persistencia de la reserva en base de datos.</li>
     *   <li>Publicación de evento asincrónico a la cola de mensajería (RF17).</li>
     * </ol>
     *
     * @param driverId Identificador del conductor autenticado
     * @param request Datos del checkout (retención, medio de pago, consentimiento)
     * @param authToken Token JWT del conductor para llamadas inter-servicio
     * @return Resultado del checkout con la reserva confirmada y detalles de la seña
     * @throws GracePeriodNotAcceptedException si no se aceptó la tolerancia de 15 minutos
     * @throws PaymentMethodRequiredException si el conductor no posee medio de pago válido
     */
    CheckoutResult processBookingCheckout(Long driverId, CheckoutBookingRequest request, String authToken);
}
