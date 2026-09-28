package com.ecopedia.charging.booking.domain;

/**
 * Puerto de salida de Reservas hacia la mensajería: avisa que a una reserva le pasó algo.
 *
 * <p>Es el mismo patrón que {@link ConnectorCatalog} y que el {@code PaymentGatewayPort} de Pagos
 * —el Adapter—: la capa de negocio dice <i>qué</i> publica y no sabe que del otro lado hay un
 * broker JMS. Eso es lo que deja probar las reglas sin levantar Artemis.
 *
 * <p><b>El contrato que toda implementación tiene que cumplir: no lanzar.</b> Se llama con la
 * reserva ya guardada, así que un fallo acá no puede deshacer nada; lo único que lograría una
 * excepción es devolverle un error al conductor por una reserva que sí se hizo. Si el aviso no
 * sale, la implementación lo deja registrado y la reserva sigue su curso (RNF06).
 */
public interface BookingEventPublisher {

    void publish(BookingEvent event);
}
