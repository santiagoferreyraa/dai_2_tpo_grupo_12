package com.ecopedia.charging.booking.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interfaz del componente <i>Reservas</i>, <b>stateful</b>. Ver ARQUITECTURA_ECOPEDIA.md §2.3.
 *
 * <p><b>Esta interfaz es el componente</b>, no el controlador REST, igual que en Terminales.
 *
 * <p><b>Por qué es stateful.</b> Entre que el conductor elige un slot y que confirma, el slot
 * está <i>retenido</i>: ni libre ni vendido. Esa retención ({@link Hold}) es estado
 * conversacional —vive por conductor y por intento, y vence sola— y la sostiene el componente
 * mismo, no la base. El contenedor crea el componente, lo inicializa y lo destruye; ver los
 * callbacks de {@code BookingServiceImpl}.
 *
 * <p><b>Contrato publicado antes que la implementación completa, a propósito.</b> Igual que el
 * commit semilla de Terminales: las firmas quedan fijas para que los tickets avancen en paralelo
 * contra el mismo contrato. ECO-31 implementó la retención y su ciclo de vida; ECO-32, confirmar
 * y cancelar; ECO-33, la disponibilidad.
 *
 * <p><b>El cruce contra las reservas guardadas lo trajo ECO-32 y no ECO-33</b>, que es donde lo
 * había dejado anotado ECO-31: es el criterio de aceptación de RF08 —confirmada la reserva, el
 * conector queda bloqueado para el resto durante esa ventana— y sin él confirmar no significaría
 * nada. ECO-33 sumó {@link #getAvailability}, que es el problema distinto de calcular los huecos
 * libres.
 *
 * <p><b>El cruce con una carga sin reserva todavía no existe</b>, aunque el ticket de ECO-33 lo
 * nombre: la carga sin reserva es RF10 y entra con SesionesDeCarga. Cuando entre, ocupa el mismo
 * calendario que una reserva, así que el cruce y la disponibilidad la ven sin cambiar de forma.
 *
 * <p>Las operaciones internas que consume SesionesDeCarga —la ventana disponible de un walk-in,
 * consumir y liberar una reserva— se suman con ese componente.
 *
 * <p>Cubre RF08 y, más adelante, RF09 y RF10.
 */
public interface BookingService {

    /**
     * Retiene un slot para un conductor mientras confirma (ECO-31).
     *
     * <p>Verifica que el conector exista y no esté fuera de servicio, y que nadie más tenga
     * comprometida una ventana que se cruce con esta —ni retenida ni ya reservada—. La retención
     * vence sola pasado el plazo configurado ({@code ecopedia.booking.hold-ttl}).
     *
     * @throws ConnectorNotFoundException si el conector no existe
     * @throws ConnectorNotBookableException si el conector está fuera de servicio
     * @throws SlotUnavailableException si la ventana se cruza con otra retención vigente o con
     *     una reserva confirmada
     * @throws InvalidBookingRequestException si la ventana ya empezó
     */
    Hold startHold(Long connectorId, TimeWindow window, Long driverId);

    /**
     * Consulta una retención vigente por id (ECO-35, para uso del Facade de Checkout).
     * Devuelve vacía si no existe o ya venció.
     */
    Optional<Hold> findHold(UUID holdId);

    /**
     * Convierte una retención vigente en una reserva guardada (ECO-32).
     *
     * <p>Es el momento en que el slot pasa de retenido a vendido: desde acá el conector queda
     * bloqueado para el resto durante esa ventana (RF08), y la reserva sobrevive a un reinicio
     * del proceso porque ya es un compromiso del conductor.
     *
     * <p>Recibe el conductor y no la tarjeta, que es lo que dice el documento: en esta entrega
     * no se cobra la seña, así que la tarjeta todavía no tiene para qué viajar. El conductor sí,
     * porque solo quien retuvo el slot lo puede confirmar.
     *
     * @throws HoldNotFoundException si no hay ninguna retención con ese id
     * @throws HoldExpiredException si la retención venció antes de confirmarse
     * @throws BookingAccessDeniedException si la retención es de otro conductor
     * @throws SlotUnavailableException si mientras tanto se guardó otra reserva que se cruza
     */
    Booking confirmBooking(UUID holdId, Long driverId);

    /**
     * Cancela una reserva del propio conductor y libera su ventana (ECO-32).
     *
     * <p>Es idempotente: cancelar dos veces la misma reserva no es un error, porque el resultado
     * que el conductor pidió —que la reserva no valga— ya se cumplió. Importa para el front, que
     * puede reintentar un pedido que se cortó sin tener que preguntar antes cómo quedó.
     *
     * @throws BookingNotFoundException si no hay ninguna reserva con ese id
     * @throws BookingAccessDeniedException si la reserva es de otro conductor
     * @throws InvalidBookingRequestException si la ventana ya terminó: no hay nada que liberar
     */
    void cancelBooking(Long bookingId, Long driverId);

    /**
     * Las reservas de un conductor, de la más próxima a la más lejana (ECO-32).
     *
     * <p>No estaba en el contrato de ECO-31 y se suma acá porque sin esto la cancelación no se
     * puede usar: el conductor necesita ver sus reservas para elegir cuál cancelar, y la portada
     * necesita la próxima para la tarjeta de "tu próxima carga".
     *
     * <p>Incluye las canceladas. Filtrar es decisión de quien muestra, y una lista que esconde
     * lo que el conductor canceló le impide verificar que efectivamente se canceló.
     */
    List<Booking> getDriverBookings(Long driverId);

    /**
     * Las ventanas libres de un conector dentro de un rango, en orden (ECO-33).
     *
     * <p>Libre es lo que nadie tiene comprometido: ni una reserva confirmada ni una retención
     * vigente. La retención cuenta aunque todavía no sea de nadie, porque mientras dura nadie más
     * la puede tomar, y ofrecerla como libre sería ofrecer un 409.
     *
     * <p>El rango se recorta a lo reservable: no empieza antes de ahora ni termina después del
     * horizonte máximo. Un rango que queda vacío después del recorte es un pedido mal armado, no un
     * conector sin huecos.
     *
     * <p><b>Es una foto, no una promesa.</b> No toma el candado: entre que el conductor ve un hueco
     * y lo retiene, otro se lo puede ganar. La garantía contra la doble reserva sigue estando en
     * {@link #startHold} y {@link #confirmBooking}, que es donde tiene que estar.
     *
     * <p>Una lista vacía significa que el conector funciona y está todo tomado. Un conector fuera
     * de servicio no devuelve lista vacía sino {@link ConnectorNotBookableException}: son dos cosas
     * distintas para el conductor, y el front las tiene que poder decir distinto.
     *
     * @throws InvalidBookingRequestException si el rango está invertido, ya pasó o queda entero
     *     fuera del horizonte
     * @throws ConnectorNotFoundException si el conector no existe
     * @throws ConnectorNotBookableException si el conector está fuera de servicio
     */
    List<TimeWindow> getAvailability(Long connectorId, Instant from, Instant to);
}
