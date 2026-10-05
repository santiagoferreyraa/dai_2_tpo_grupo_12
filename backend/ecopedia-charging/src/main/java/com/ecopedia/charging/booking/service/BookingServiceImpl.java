package com.ecopedia.charging.booking.service;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingAccessDeniedException;
import com.ecopedia.charging.booking.domain.BookingEvent;
import com.ecopedia.charging.booking.domain.BookingEventPublisher;
import com.ecopedia.charging.booking.domain.BookingNotFoundException;
import com.ecopedia.charging.booking.domain.BookingRepository;
import com.ecopedia.charging.booking.domain.BookingService;
import com.ecopedia.charging.booking.domain.BookingStatus;
import com.ecopedia.charging.booking.domain.ConnectorCatalog;
import com.ecopedia.charging.booking.domain.ConnectorNotBookableException;
import com.ecopedia.charging.booking.domain.ConnectorNotFoundException;
import com.ecopedia.charging.booking.domain.ConnectorSnapshot;
import com.ecopedia.charging.booking.domain.FreeWindows;
import com.ecopedia.charging.booking.domain.Hold;
import com.ecopedia.charging.booking.domain.HoldExpiredException;
import com.ecopedia.charging.booking.domain.HoldNotFoundException;
import com.ecopedia.charging.booking.domain.InvalidBookingRequestException;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.booking.domain.TimeWindow;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Implementación del componente <i>Reservas</i>: el único stateful construido hasta ahora.
 *
 * <p><b>Dónde está el estado.</b> En {@link #holds}: las retenciones vigentes, una por intento de
 * reserva. Es un campo de instancia de un bean que crea y destruye el contenedor, <b>no</b> un
 * {@code static}: su vida es la del componente, y la del componente la decide Spring. Eso es lo
 * que pide la consigna y lo que se muestra en la demo.
 *
 * <p><b>La evidencia del ciclo de vida son los dos callbacks</b>, y cada uno deja una línea en el
 * log:
 *
 * <ul>
 *   <li>{@link #start()} con {@code @PostConstruct}: el contenedor ya inyectó todo y, antes de
 *       que llegue el primer pedido, el componente arma el reloj que vence las retenciones.
 *   <li>{@link #stop()} con {@code @PreDestroy}: al bajar el proceso, el contenedor le avisa;
 *       el componente apaga el reloj y suelta las retenciones que nadie confirmó.
 * </ul>
 *
 * <p><b>Las dos mitades de un slot comprometido.</b> Una ventana puede estar tomada por dos
 * motivos distintos y el componente consulta los dos, siempre juntos:
 *
 * <ul>
 *   <li>una <b>retención</b> vigente, en memoria, de alguien que está por confirmar;
 *   <li>una <b>reserva</b> confirmada, en la base, de alguien que ya se comprometió.
 * </ul>
 *
 * Lo primero dura minutos y se pierde con el proceso; lo segundo dura hasta su ventana y
 * sobrevive a un reinicio, porque el conductor ya cuenta con él. Ese par es lo que hace cumplir
 * RF08: confirmada la reserva, el conector queda bloqueado para el resto durante esa ventana.
 *
 * <p><b>Por qué las retenciones no van a la base.</b> Duran minutos, vencen solas y no son un
 * compromiso de nadie todavía. Guardarlas sería llenar la base de reservas fantasma y obligar a
 * un proceso aparte a limpiarlas. Si el proceso se reinicia, se pierden, y está bien: el
 * conductor vuelve a elegir el slot. Lo que sí se guarda es la reserva confirmada.
 */
@Service
public class BookingServiceImpl implements BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingServiceImpl.class);

    private final ConnectorCatalog connectorCatalog;
    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;
    private final Clock clock;
    private final Duration holdTtl;
    private final Duration maxWindow;
    private final Duration maxHorizon;

    /** El estado conversacional del componente: las retenciones vigentes, por id. */
    private final Map<UUID, Hold> holds = new ConcurrentHashMap<>();

    /**
     * Candado para "revisar si el slot está libre y comprometerlo".
     *
     * <p>El mapa ya tolera accesos concurrentes, pero eso no alcanza: son DOS pasos, y si dos
     * conductores piden el mismo slot en el mismo instante, los dos pueden revisar, ver el slot
     * libre y tomarlo. Es exactamente el caso que §2.1 dice que este componente evita.
     *
     * <p>Lo toman las dos operaciones que comprometen una ventana —retener y confirmar—, y es el
     * mismo candado a propósito: si fueran dos, una confirmación y una retención podrían cruzarse
     * sobre la misma ventana sin verse.
     *
     * <p><b>Alcanza mientras Reservas corra en un solo proceso</b>, que es como se despliega y
     * como se demuestra. Con dos instancias detrás de un balanceador cada una tendría su candado
     * y sus retenciones, y la garantía tendría que bajar a la base —una restricción de exclusión
     * sobre {@code (connector_id, tstzrange(window_start, window_end))} en PostgreSQL—. No se
     * hizo ahora porque H2, que es el motor del perfil {@code dev}, no la soporta y las
     * migraciones son las mismas para los dos.
     */
    private final Object holdLock = new Object();

    /** El reloj que vence las retenciones. Lo crea {@link #start()} y lo apaga {@link #stop()}. */
    private ScheduledExecutorService expirations;

    public BookingServiceImpl(
            ConnectorCatalog connectorCatalog,
            BookingRepository bookingRepository,
            BookingEventPublisher eventPublisher,
            Clock clock,
            @Value("${ecopedia.booking.hold-ttl}") Duration holdTtl,
            @Value("${ecopedia.booking.max-window}") Duration maxWindow,
            @Value("${ecopedia.booking.max-horizon}") Duration maxHorizon) {
        this.connectorCatalog = connectorCatalog;
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.holdTtl = holdTtl;
        this.maxWindow = maxWindow;
        this.maxHorizon = maxHorizon;
    }

    /**
     * Callback de inicialización: lo invoca el contenedor una sola vez, con las dependencias ya
     * inyectadas y antes de que el componente atienda un pedido.
     *
     * <p>El reloj se arma acá y no en el constructor porque es un recurso con ciclo de vida
     * —un hilo—, y quien lo abre tiene que ser quien sabe cuándo cerrarlo.
     */
    @PostConstruct
    void start() {
        expirations = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "booking-hold-expiration");
            // Daemon: si algo falla antes del @PreDestroy, este hilo no mantiene vivo el proceso.
            thread.setDaemon(true);
            return thread;
        });
        log.info(
                "BookingService iniciado por el contenedor (@PostConstruct): las retenciones vencen a los {}", holdTtl);
    }

    /**
     * Callback de destrucción: lo invoca el contenedor al cerrarse, antes de soltar el bean.
     *
     * <p>Sin esto el hilo del reloj quedaría huérfano. Y el log deja constancia de cuántas
     * retenciones se perdieron con el reinicio, que es información útil y no un error. Las
     * reservas confirmadas no aparecen acá porque no se pierden: están en la base.
     */
    @PreDestroy
    void stop() {
        int pending = holds.size();
        expirations.shutdownNow();
        holds.clear();
        log.info(
                "BookingService destruido por el contenedor (@PreDestroy): se liberaron {} retenciones sin confirmar",
                pending);
    }

    @Override
    public Hold startHold(Long connectorId, TimeWindow window, Long driverId) {
        Instant now = clock.instant();
        requireSensibleWindow(window, now);

        /*
         * El conector se consulta ANTES de tomar el candado: es un pedido de red a core y puede
         * tardar hasta el plazo del cliente. Con el candado tomado, un core lento frenaría las
         * retenciones de todos los conectores, no solo las de este.
         *
         * El cruce contra las reservas guardadas, en cambio, sí va adentro: es una consulta
         * indexada a la base local, y sacarla del candado abriría la ventana de tiempo que el
         * candado existe para cerrar.
         */
        requireBookableConnector(connectorId);

        Hold hold;
        synchronized (holdLock) {
            requireFreeSlot(connectorId, window, now);

            hold = new Hold(UUID.randomUUID(), connectorId, driverId, window, now.plus(holdTtl));
            holds.put(hold.id(), hold);
        }

        expirations.schedule(() -> expire(hold.id()), holdTtl.toMillis(), TimeUnit.MILLISECONDS);
        log.info(
                "Retención {} del conector {} para el conductor {}: vence a las {}",
                hold.id(),
                connectorId,
                driverId,
                hold.expiresAt());
        return hold;
    }

    /**
     * Que la ventana pedida sea una ventana que tenga sentido reservar.
     *
     * <p>Sin techo, un solo pedido bloquea un conector por años: la regla de cruce haría su
     * trabajo perfectamente —el conector queda bloqueado para el resto, tal cual dice RF08— y el
     * resultado sería igualmente un conector inutilizable. No hace falta mala intención: un front
     * con un error de fechas manda lo mismo.
     *
     * <p>Los dos topes salen de configuración y no están escritos acá porque son una decisión de
     * negocio, no de código: cuánto puede durar una carga y con cuánta anticipación se reserva
     * dependen del operador. Ver {@code ecopedia.booking} en {@code application.yml}.
     *
     * @throws InvalidBookingRequestException si la ventana ya empezó, dura de más o está de más lejos
     */
    private void requireSensibleWindow(TimeWindow window, Instant now) {
        if (window.start().isBefore(now)) {
            throw new InvalidBookingRequestException("La ventana ya empezó: las reservas son a futuro");
        }
        if (Duration.between(window.start(), window.end()).compareTo(maxWindow) > 0) {
            throw new InvalidBookingRequestException("Una reserva no puede durar más de " + maxWindow.toHours()
                    + " horas. Para una ventana más larga hay que encadenar reservas.");
        }
        if (window.start().isAfter(now.plus(maxHorizon))) {
            throw new InvalidBookingRequestException(
                    "No se puede reservar con más de " + maxHorizon.toDays() + " días de anticipación");
        }
    }

    /**
     * Que el conector exista y no esté fuera de servicio. Es un pedido de red a core: nunca con
     * {@link #holdLock} tomado.
     *
     * @throws ConnectorNotFoundException si el conector no existe
     * @throws ConnectorNotBookableException si el conector está fuera de servicio
     */
    private void requireBookableConnector(Long connectorId) {
        ConnectorSnapshot connector = connectorCatalog
                .findConnector(connectorId)
                .orElseThrow(() -> new ConnectorNotFoundException(connectorId));
        if (connector.isOutOfService()) {
            throw new ConnectorNotBookableException(connectorId);
        }
    }

    /** Para retener: no hay ninguna retención propia todavía de la que excluirse. */
    private void requireFreeSlot(Long connectorId, TimeWindow window, Instant now) {
        requireFreeSlot(connectorId, window, now, null);
    }

    /**
     * Que nadie tenga comprometida una ventana que se cruce con esta, ni reteniéndola ni
     * habiéndola reservado. <b>Se invoca siempre con {@link #holdLock} tomado.</b>
     *
     * <p>{@code ownHoldId} es la retención que el propio pedido ya tiene sobre esa ventana, y que
     * por eso no cuenta como conflicto: al confirmar, la retención sigue en el mapa —se suelta
     * recién cuando la reserva está guardada— y sin excluirla el conductor chocaría contra sí
     * mismo. Es {@code null} cuando todavía no hay ninguna.
     *
     * @throws SlotUnavailableException si el slot está tomado
     */
    private void requireFreeSlot(Long connectorId, TimeWindow window, Instant now, UUID ownHoldId) {
        /*
         * Una retención vencida no bloquea aunque el reloj todavía no la haya sacado del mapa:
         * el reloj es la limpieza, y la regla es la hora.
         */
        boolean held = holds.values().stream()
                .filter(other -> !other.id().equals(ownHoldId))
                .anyMatch(other -> other.connectorId().equals(connectorId)
                        && !other.isExpiredAt(now)
                        && other.window().overlaps(window));
        if (held) {
            throw new SlotUnavailableException(connectorId);
        }

        // Y lo que hace cumplir RF08: una reserva confirmada bloquea el conector por su ventana.
        boolean booked =
                bookingRepository.existsOverlapping(connectorId, BookingStatus.CONFIRMED, window.start(), window.end());
        if (booked) {
            throw new SlotUnavailableException(connectorId);
        }
    }

    /** Lo ejecuta el reloj al cumplirse el plazo. Si ya se confirmó, no queda nada que sacar. */
    private void expire(UUID holdId) {
        Hold expired = holds.remove(holdId);
        if (expired != null) {
            log.info(
                    "Venció la retención {} del conector {}: el slot vuelve a estar libre",
                    holdId,
                    expired.connectorId());
        }
    }

    /** Cuántas retenciones hay vigentes. Para los tests: el estado se ve desde afuera sin exponerlo. */
    int activeHoldCount() {
        return holds.size();
    }

    @Override
    public Optional<Hold> findHold(UUID holdId) {
        Hold hold = holds.get(holdId);
        if (hold == null || hold.isExpiredAt(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(hold);
    }

    @Override
    public Booking confirmBooking(UUID holdId, Long driverId) {
        Instant now = clock.instant();
        Booking booking;

        /*
         * Todo el paso adentro del candado, y en este orden: verificar, guardar, y recién después
         * soltar la retención. Si la retención se sacara primero, entre eso y el guardado la
         * ventana quedaría sin dueño y otro conductor podría colarse.
         *
         * Nótese que NO hay @Transactional acá. Lo hay, y es lo que se necesita, dentro de
         * save(): así la reserva queda confirmada en la base ANTES de que este hilo suelte el
         * candado. Con una transacción abierta en este método, el commit ocurriría al salir —ya
         * sin candado—, y el siguiente conductor podría consultar la base y no ver todavía la
         * reserva recién guardada. Sería exactamente la doble reserva que el candado evita.
         */
        synchronized (holdLock) {
            Hold hold = holds.get(holdId);
            if (hold == null) {
                throw new HoldNotFoundException(holdId);
            }
            if (!hold.driverId().equals(driverId)) {
                // Sin sacarla del mapa: la retención sigue siendo válida para su dueño.
                throw BookingAccessDeniedException.forHold(holdId);
            }
            if (hold.isExpiredAt(now)) {
                holds.remove(holdId);
                throw new HoldExpiredException(holdId);
            }

            /*
             * Verificar de nuevo, aunque startHold ya lo hizo. Entre una cosa y la otra pasaron
             * minutos, y el estado que se consultó entonces es de la base, que este proceso no es
             * el único que puede escribir: una reserva cargada a mano o un segundo proceso
             * alcanzan. Es una consulta indexada contra un tiempo de espera humano.
             */
            requireFreeSlot(hold.connectorId(), hold.window(), now, holdId);

            booking = bookingRepository.save(new Booking(hold.connectorId(), driverId, hold.window(), now));
            holds.remove(holdId);
        }

        log.info(
                "Reserva {} confirmada: el conector {} queda bloqueado para el conductor {} entre {} y {}",
                booking.getId(),
                booking.getConnectorId(),
                driverId,
                booking.getWindow().start(),
                booking.getWindow().end());

        /*
         * El aviso sale DESPUÉS de que la reserva quedó guardada —save() ya hizo commit, ver
         * arriba— y fuera del candado. Lo primero es para no avisar una reserva que al final no
         * se guardó; lo segundo, para que un broker lento no frene a los otros conductores que
         * esperan el candado. Si algún día este método pasa a tener @Transactional, la
         * publicación tiene que moverse a después del commit.
         */
        eventPublisher.publish(BookingEvent.confirmed(booking, now));
        return booking;
    }

    @Override
    public void cancelBooking(Long bookingId, Long driverId) {
        Booking booking =
                bookingRepository.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (!booking.belongsTo(driverId)) {
            throw BookingAccessDeniedException.forBooking(bookingId);
        }
        if (booking.isCancelled()) {
            // Idempotente: lo que el conductor pidió ya está hecho, así que no es un error.
            return;
        }
        /*
         * Una ventana que ya terminó no se cancela: no hay nada que liberar, y dejar reescribirla
         * sería permitir borrar el historial de una reserva que el conductor usó o a la que no se
         * presentó. Lo segundo es RF09 y tiene su propio estado.
         */
        if (!booking.getWindow().end().isAfter(clock.instant())) {
            throw new InvalidBookingRequestException("La reserva " + bookingId + " ya terminó y no se puede cancelar");
        }

        booking.cancel();
        bookingRepository.save(booking);
        log.info(
                "Reserva {} cancelada por el conductor {}: el conector {} vuelve a estar libre entre {} y {}",
                bookingId,
                driverId,
                booking.getConnectorId(),
                booking.getWindow().start(),
                booking.getWindow().end());

        // Solo la primera cancelación avisa: la segunda ya salió por el `return` de arriba.
        eventPublisher.publish(BookingEvent.cancelled(booking, clock.instant()));
    }

    @Override
    public List<Booking> getDriverBookings(Long driverId) {
        return bookingRepository.findByDriverIdOrderByWindowStartAsc(driverId);
    }

    @Override
    public List<TimeWindow> getAvailability(Long connectorId, Instant from, Instant to) {
        Instant now = clock.instant();
        TimeWindow range = reservableRange(new TimeWindow(from, to), now);
        requireBookableConnector(connectorId);

        /*
         * Sin candado, a propósito: es una lectura y su resultado es una foto. Si otro conductor
         * retiene un hueco un instante después, el que lo quiera tomar recibe 409 en startHold,
         * que es donde vive la garantía. Tomar el candado acá frenaría todas las retenciones de
         * todos los conectores por cada vistazo a la agenda, sin volver la foto más cierta.
         */
        List<TimeWindow> occupied = new ArrayList<>();
        bookingRepository
                .findOverlapping(connectorId, BookingStatus.CONFIRMED, range.start(), range.end())
                .forEach(booking -> occupied.add(booking.getWindow()));
        // La misma regla que requireFreeSlot: una retención vencida no ocupa aunque siga en el mapa.
        holds.values().stream()
                .filter(hold -> hold.connectorId().equals(connectorId) && !hold.isExpiredAt(now))
                .forEach(hold -> occupied.add(hold.window()));

        return FreeWindows.within(range, occupied);
    }

    /**
     * El rango pedido, recortado a lo que se puede reservar: desde ahora y hasta el horizonte.
     *
     * <p>Se recorta en vez de rechazar porque "de hoy a fin de mes" es un pedido razonable aunque
     * hoy ya haya empezado. Lo que no tiene sentido es un rango que después del recorte no deja
     * nada: ese sí es un error de quien pregunta, y contestarle con una lista vacía le haría creer
     * que el conector está todo reservado.
     */
    private TimeWindow reservableRange(TimeWindow requested, Instant now) {
        Instant horizon = now.plus(maxHorizon);
        if (!requested.end().isAfter(now)) {
            throw new InvalidBookingRequestException("El rango ya terminó: la disponibilidad es a futuro");
        }
        if (!requested.start().isBefore(horizon)) {
            throw new InvalidBookingRequestException(
                    "No se puede consultar con más de " + maxHorizon.toDays() + " días de anticipación");
        }
        Instant start = requested.start().isBefore(now) ? now : requested.start();
        Instant end = requested.end().isAfter(horizon) ? horizon : requested.end();
        return new TimeWindow(start, end);
    }
}
