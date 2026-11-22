package com.ecopedia.integration.payment.domain;

/**
 * Puerto de salida de Pagos hacia la mensajería: avisa que a una deuda le pasó algo (ECO-62).
 *
 * <p>El mismo patrón que {@code BookingEventPublisher} en Reservas: el negocio dice <i>qué</i> publica
 * y no sabe que del otro lado hay un broker.
 *
 * <p><b>El contrato: no lanzar.</b> Se llama con la deuda ya guardada (y, si se cobró, con la plata ya
 * cobrada): un fallo acá no puede deshacer nada, y una excepción solo le devolvería un error al
 * conductor por un pago que sí salió.
 */
public interface DebtEventPublisher {

    void publish(DebtEvent event);
}
