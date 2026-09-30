package com.ecopedia.async.notification.domain;

/**
 * Componente <i>Notificaciones</i> (RF17): avisa al conductor lo que les pasa a sus reservas.
 *
 * <p><b>Nadie lo llama directamente.</b> Su entrada es la cola {@code notifications.dispatch}: el
 * listener de {@code messaging} convierte cada mensaje en un {@link NotificationToDispatch} y lo
 * pasa acá. Por eso el envío de un aviso no puede demorar ni hacer fallar una reserva (RNF05).
 *
 * <p>Es <b>stateless</b>: guarda un historial, pero eso es persistencia del dominio, no estado
 * conversacional que el contenedor tenga que cuidar entre una llamada y la siguiente.
 */
public interface NotificationService {

    /**
     * Arma el aviso, lo guarda en el historial del destinatario y lo manda por mail.
     *
     * <p>Idempotente por {@link NotificationToDispatch#eventId()}: si el mismo hecho llega dos
     * veces —el broker reentrega, o alguien publicó dos veces—, se guarda y se manda una sola.
     */
    void dispatch(NotificationToDispatch request);

    /**
     * Los avisos del conductor, del más nuevo al más viejo, con cuántos tiene sin leer.
     *
     * @param afterId si viene, solo los posteriores a ese aviso: es lo que usa la pantalla para
     *     preguntar cada tantos segundos si llegó algo, sin volver a bajar todo el historial.
     */
    NotificationFeed getHistory(Long recipientId, Long afterId);

    /** Marca un aviso como leído. Solo su destinatario puede hacerlo. */
    void markAsRead(Long notificationId, Long recipientId);

    /** Marca como leídos todos los avisos del conductor. */
    void markAllAsRead(Long recipientId);

    /**
     * Saca un aviso del buzón del conductor. Solo su destinatario puede hacerlo.
     *
     * <p>Idempotente: borrar dos veces el mismo aviso es el mismo pedido repetido. Lo que no es
     * idempotente es borrar el de otro, que sigue siendo un 403.
     */
    void delete(Long notificationId, Long recipientId);

    /** Vacía el buzón del conductor de una sola vez. */
    void deleteAll(Long recipientId);
}
