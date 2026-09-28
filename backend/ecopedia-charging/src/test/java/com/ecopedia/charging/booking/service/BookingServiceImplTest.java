package com.ecopedia.charging.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecopedia.charging.booking.domain.Booking;
import com.ecopedia.charging.booking.domain.BookingAccessDeniedException;
import com.ecopedia.charging.booking.domain.BookingEvent;
import com.ecopedia.charging.booking.domain.BookingEventType;
import com.ecopedia.charging.booking.domain.BookingNotFoundException;
import com.ecopedia.charging.booking.domain.BookingStatus;
import com.ecopedia.charging.booking.domain.ConnectorCatalog;
import com.ecopedia.charging.booking.domain.ConnectorNotBookableException;
import com.ecopedia.charging.booking.domain.ConnectorNotFoundException;
import com.ecopedia.charging.booking.domain.ConnectorSnapshot;
import com.ecopedia.charging.booking.domain.Hold;
import com.ecopedia.charging.booking.domain.HoldExpiredException;
import com.ecopedia.charging.booking.domain.HoldNotFoundException;
import com.ecopedia.charging.booking.domain.InvalidBookingRequestException;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.booking.domain.TimeWindow;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * El estado de Reservas, su ciclo de vida (ECO-31) y la confirmación de la reserva (ECO-32),
 * probados sin levantar Spring.
 *
 * <p>Los callbacks se invocan a mano —{@code start()} y {@code stop()}— en el orden en que los
 * invoca el contenedor. Así cada prueba dice explícitamente en qué momento de la vida del
 * componente está, y la que prueba el vencimiento no depende de cuánto tarde en arrancar un
 * contexto entero.
 *
 * <p>La persistencia es {@link InMemoryBookingRepository}: lo que se prueba acá son las reglas,
 * no el SQL. La consulta real contra la base la verifica {@code BookingApiTest}.
 */
class BookingServiceImplTest {

    private static final Long CONNECTOR = 7L;
    private static final Long DRIVER = 42L;
    private static final Long OTHER_DRIVER = 43L;

    /** Un conector que existe y está libre. */
    private static final ConnectorCatalog AVAILABLE = id -> Optional.of(new ConnectorSnapshot(id, 1L, "AVAILABLE"));

    private BookingServiceImpl service;
    private MutableClock clock;
    private RecordingBookingEventPublisher events;

    @AfterEach
    void destroy() {
        if (service != null) {
            service.stop();
        }
    }

    /** Los mismos topes que trae la configuración por omisión. */
    private static final Duration MAX_WINDOW = Duration.ofHours(4);

    private static final Duration MAX_HORIZON = Duration.ofDays(30);

    /** El componente recién inicializado por el "contenedor", con el plazo de retención pedido. */
    private BookingServiceImpl started(ConnectorCatalog catalog, Duration holdTtl) {
        clock = new MutableClock(Instant.parse("2026-09-14T12:00:00Z"));
        events = new RecordingBookingEventPublisher();
        service = new BookingServiceImpl(
                catalog, new InMemoryBookingRepository(), events, clock, holdTtl, MAX_WINDOW, MAX_HORIZON);
        service.start();
        return service;
    }

    /** Una ventana de una hora que empieza dentro de {@code hoursFromNow} horas. */
    private TimeWindow windowIn(long hoursFromNow) {
        Instant start = clock.instant().plus(Duration.ofHours(hoursFromNow));
        return new TimeWindow(start, start.plus(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("La retención vence sola y el slot vuelve a quedar libre")
    void holdExpiresOnItsOwn() throws InterruptedException {
        started(AVAILABLE, Duration.ofMillis(150));
        TimeWindow window = windowIn(2);

        service.startHold(CONNECTOR, window, DRIVER);
        assertThat(service.activeHoldCount()).isEqualTo(1);

        // El vencimiento lo dispara el reloj del componente, no la prueba: se espera a que pase.
        long deadline = System.currentTimeMillis() + 3_000;
        while (service.activeHoldCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertThat(service.activeHoldCount()).isZero();

        // La prueba de fondo: otro conductor puede tomar exactamente el mismo slot.
        Hold retaken = service.startHold(CONNECTOR, window, OTHER_DRIVER);
        assertThat(retaken.driverId()).isEqualTo(OTHER_DRIVER);
    }

    @Test
    @DisplayName("No se puede retener dos veces una ventana que se cruza, en el mismo conector")
    void rejectsOverlappingHoldOnTheSameConnector() {
        started(AVAILABLE, Duration.ofMinutes(10));
        TimeWindow window = windowIn(2);
        service.startHold(CONNECTOR, window, DRIVER);

        TimeWindow halfOverlapping = new TimeWindow(
                window.start().plus(Duration.ofMinutes(30)), window.end().plus(Duration.ofMinutes(30)));

        assertThatThrownBy(() -> service.startHold(CONNECTOR, window, OTHER_DRIVER))
                .isInstanceOf(SlotUnavailableException.class);
        assertThatThrownBy(() -> service.startHold(CONNECTOR, halfOverlapping, OTHER_DRIVER))
                .isInstanceOf(SlotUnavailableException.class);
    }

    /*
     * La contracara de la anterior: la regla no puede ser tan gruesa que bloquee lo que no se
     * pisa. Un turno pegado al otro y el mismo turno en otro conector tienen que poder retenerse.
     */
    @Test
    @DisplayName("Una ventana pegada, o la misma en otro conector, sí se puede retener")
    void allowsAdjacentWindowsAndOtherConnectors() {
        started(AVAILABLE, Duration.ofMinutes(10));
        TimeWindow window = windowIn(2);
        service.startHold(CONNECTOR, window, DRIVER);

        TimeWindow rightAfter = new TimeWindow(window.end(), window.end().plus(Duration.ofHours(1)));

        service.startHold(CONNECTOR, rightAfter, OTHER_DRIVER);
        service.startHold(CONNECTOR + 1, window, OTHER_DRIVER);
        assertThat(service.activeHoldCount()).isEqualTo(3);
    }

    /*
     * La regla es la hora, no el hilo que limpia (ECO-33). Con el plazo en minutos el hilo no
     * llega a sacar la retención, y aun así, pasado el plazo, el slot tiene que estar libre: si
     * dependiera del hilo, un reloj atrasado bloquearía slots que ya nadie tiene.
     */
    @Test
    @DisplayName("Una retención vencida no bloquea, aunque el reloj todavía no la haya sacado")
    void expiredHoldDoesNotBlockBeforeItIsSwept() {
        started(AVAILABLE, Duration.ofMinutes(10));
        TimeWindow window = windowIn(2);
        service.startHold(CONNECTOR, window, DRIVER);

        clock.advance(Duration.ofMinutes(11));
        assertThat(service.activeHoldCount()).isEqualTo(1);

        Hold retaken = service.startHold(CONNECTOR, window, OTHER_DRIVER);
        assertThat(retaken.driverId()).isEqualTo(OTHER_DRIVER);
    }

    @Test
    @DisplayName("Al destruirse el componente (@PreDestroy) se liberan las retenciones")
    void destroyReleasesPendingHolds() {
        started(AVAILABLE, Duration.ofMinutes(10));
        service.startHold(CONNECTOR, windowIn(2), DRIVER);
        service.startHold(CONNECTOR, windowIn(4), DRIVER);

        service.stop();

        assertThat(service.activeHoldCount()).isZero();
        service = null; // ya destruido: el @AfterEach no lo vuelve a destruir
    }

    @Test
    @DisplayName("Un conector fuera de servicio no se retiene")
    void rejectsOutOfServiceConnector() {
        started(id -> Optional.of(new ConnectorSnapshot(id, 1L, "OUT_OF_SERVICE")), Duration.ofMinutes(10));

        assertThatThrownBy(() -> service.startHold(CONNECTOR, windowIn(2), DRIVER))
                .isInstanceOf(ConnectorNotBookableException.class);
        assertThat(service.activeHoldCount()).isZero();
    }

    /* Ocupado AHORA no importa: la reserva es para más tarde, y quien carga ya se habrá ido. */
    @Test
    @DisplayName("Un conector ocupado ahora sí se puede retener para más tarde")
    void allowsCurrentlyOccupiedConnector() {
        started(id -> Optional.of(new ConnectorSnapshot(id, 1L, "OCCUPIED")), Duration.ofMinutes(10));

        service.startHold(CONNECTOR, windowIn(2), DRIVER);
        assertThat(service.activeHoldCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un conector que no existe se rechaza")
    void rejectsUnknownConnector() {
        started(id -> Optional.empty(), Duration.ofMinutes(10));

        assertThatThrownBy(() -> service.startHold(CONNECTOR, windowIn(2), DRIVER))
                .isInstanceOf(ConnectorNotFoundException.class);
    }

    @Test
    @DisplayName("Una ventana que ya empezó se rechaza")
    void rejectsWindowInThePast() {
        started(AVAILABLE, Duration.ofMinutes(10));
        Instant start = clock.instant().minus(Duration.ofMinutes(5));

        assertThatThrownBy(() ->
                        service.startHold(CONNECTOR, new TimeWindow(start, start.plus(Duration.ofHours(1))), DRIVER))
                .isInstanceOf(InvalidBookingRequestException.class);
    }

    /*
     * El techo de la ventana no es una formalidad: sin él, una sola reserva de cinco años deja el
     * conector inutilizable, y la regla de cruce lo cumpliría al pie de la letra.
     */
    @Test
    @DisplayName("Una ventana más larga que el máximo se rechaza")
    void rejectsWindowLongerThanAllowed() {
        started(AVAILABLE, Duration.ofMinutes(10));
        Instant start = clock.instant().plus(Duration.ofHours(2));

        assertThatThrownBy(() -> service.startHold(
                        CONNECTOR, new TimeWindow(start, start.plus(MAX_WINDOW).plus(Duration.ofMinutes(1))), DRIVER))
                .isInstanceOf(InvalidBookingRequestException.class);

        // Y el máximo exacto sí entra: el tope es un límite, no una franja prohibida.
        service.startHold(CONNECTOR, new TimeWindow(start, start.plus(MAX_WINDOW)), DRIVER);
        assertThat(service.activeHoldCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una ventana más allá del horizonte se rechaza")
    void rejectsWindowTooFarAway() {
        started(AVAILABLE, Duration.ofMinutes(10));
        Instant tooFar = clock.instant().plus(MAX_HORIZON).plus(Duration.ofDays(1));

        assertThatThrownBy(() ->
                        service.startHold(CONNECTOR, new TimeWindow(tooFar, tooFar.plus(Duration.ofHours(1))), DRIVER))
                .isInstanceOf(InvalidBookingRequestException.class);
    }

    /** Confirmar la retención: el slot pasa de retenido a reservado (ECO-32, RF08). */
    @Nested
    @DisplayName("Confirmar la reserva")
    class Confirming {

        @Test
        @DisplayName("La reserva queda guardada y la retención se suelta")
        void savesTheBookingAndReleasesTheHold() {
            started(AVAILABLE, Duration.ofMinutes(10));
            TimeWindow window = windowIn(2);
            Hold hold = service.startHold(CONNECTOR, window, DRIVER);

            Booking booking = service.confirmBooking(hold.id(), DRIVER);

            assertThat(booking.getId()).isNotNull();
            assertThat(booking.getConnectorId()).isEqualTo(CONNECTOR);
            assertThat(booking.getDriverId()).isEqualTo(DRIVER);
            assertThat(booking.getWindow()).isEqualTo(window);
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(booking.getCreatedAt()).isEqualTo(clock.instant());

            // La retención ya no hace falta: lo que bloquea el slot ahora es la reserva guardada.
            assertThat(service.activeHoldCount()).isZero();
        }

        /*
         * LA prueba del ticket: la definición de listo de ECO-32 es exactamente esto. Sin el
         * cruce contra las reservas guardadas, acá el segundo conductor recibiría su retención,
         * porque la del primero se soltó al confirmar.
         */
        @Test
        @DisplayName("Confirmada la reserva, el conector queda bloqueado para el resto en esa ventana")
        void blocksTheConnectorForEveryoneElse() {
            started(AVAILABLE, Duration.ofMinutes(10));
            TimeWindow window = windowIn(2);
            service.confirmBooking(service.startHold(CONNECTOR, window, DRIVER).id(), DRIVER);

            TimeWindow halfOverlapping = new TimeWindow(
                    window.start().plus(Duration.ofMinutes(30)), window.end().plus(Duration.ofMinutes(30)));

            assertThatThrownBy(() -> service.startHold(CONNECTOR, window, OTHER_DRIVER))
                    .isInstanceOf(SlotUnavailableException.class);
            assertThatThrownBy(() -> service.startHold(CONNECTOR, halfOverlapping, OTHER_DRIVER))
                    .isInstanceOf(SlotUnavailableException.class);
        }

        /* El bloqueo es de esa ventana y de ese conector, no del conector entero ni del día. */
        @Test
        @DisplayName("El bloqueo no se pasa de la ventana reservada")
        void blocksOnlyTheBookedWindow() {
            started(AVAILABLE, Duration.ofMinutes(10));
            TimeWindow window = windowIn(2);
            service.confirmBooking(service.startHold(CONNECTOR, window, DRIVER).id(), DRIVER);

            TimeWindow rightAfter = new TimeWindow(window.end(), window.end().plus(Duration.ofHours(1)));

            service.startHold(CONNECTOR, rightAfter, OTHER_DRIVER);
            service.startHold(CONNECTOR + 1, window, OTHER_DRIVER);
            assertThat(service.activeHoldCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Una retención que no existe no se confirma")
        void rejectsUnknownHold() {
            started(AVAILABLE, Duration.ofMinutes(10));

            assertThatThrownBy(() -> service.confirmBooking(UUID.randomUUID(), DRIVER))
                    .isInstanceOf(HoldNotFoundException.class);
        }

        /* Confirmar dos veces es el doble clic del conductor: la segunda no puede crear otra. */
        @Test
        @DisplayName("La misma retención no se confirma dos veces")
        void rejectsConfirmingTwice() {
            started(AVAILABLE, Duration.ofMinutes(10));
            Hold hold = service.startHold(CONNECTOR, windowIn(2), DRIVER);
            service.confirmBooking(hold.id(), DRIVER);

            assertThatThrownBy(() -> service.confirmBooking(hold.id(), DRIVER))
                    .isInstanceOf(HoldNotFoundException.class);
        }

        @Test
        @DisplayName("Nadie confirma la retención de otro conductor")
        void rejectsSomeoneElsesHold() {
            started(AVAILABLE, Duration.ofMinutes(10));
            Hold hold = service.startHold(CONNECTOR, windowIn(2), DRIVER);

            assertThatThrownBy(() -> service.confirmBooking(hold.id(), OTHER_DRIVER))
                    .isInstanceOf(BookingAccessDeniedException.class);

            // Y el intento fallido no le arruina la retención a su dueño.
            assertThat(service.activeHoldCount()).isEqualTo(1);
            assertThat(service.confirmBooking(hold.id(), DRIVER).getDriverId()).isEqualTo(DRIVER);
        }

        /*
         * El plazo se cumple según la hora, no según cuándo pase el hilo de limpieza: el reloj se
         * adelanta mientras la retención sigue en el mapa, que es el caso borde que importa.
         */
        @Test
        @DisplayName("Una retención vencida no se confirma")
        void rejectsExpiredHold() {
            started(AVAILABLE, Duration.ofMinutes(10));
            Hold hold = service.startHold(CONNECTOR, windowIn(5), DRIVER);

            clock.advance(Duration.ofMinutes(11));

            assertThatThrownBy(() -> service.confirmBooking(hold.id(), DRIVER))
                    .isInstanceOf(HoldExpiredException.class);
            assertThat(service.activeHoldCount()).isZero();
        }
    }

    /** Cancelar la reserva y liberar la ventana (ECO-32). */
    @Nested
    @DisplayName("Cancelar la reserva")
    class Cancelling {

        @Test
        @DisplayName("Cancelada la reserva, el slot vuelve a estar libre para cualquiera")
        void freesTheSlot() {
            started(AVAILABLE, Duration.ofMinutes(10));
            TimeWindow window = windowIn(2);
            Booking booking = service.confirmBooking(
                    service.startHold(CONNECTOR, window, DRIVER).id(), DRIVER);

            service.cancelBooking(booking.getId(), DRIVER);

            Hold retaken = service.startHold(CONNECTOR, window, OTHER_DRIVER);
            assertThat(retaken.driverId()).isEqualTo(OTHER_DRIVER);
            assertThat(service.getDriverBookings(DRIVER))
                    .singleElement()
                    .extracting(Booking::getStatus)
                    .isEqualTo(BookingStatus.CANCELLED);
        }

        @Test
        @DisplayName("Nadie cancela la reserva de otro conductor")
        void rejectsSomeoneElsesBooking() {
            started(AVAILABLE, Duration.ofMinutes(10));
            Booking booking = service.confirmBooking(
                    service.startHold(CONNECTOR, windowIn(2), DRIVER).id(), DRIVER);

            assertThatThrownBy(() -> service.cancelBooking(booking.getId(), OTHER_DRIVER))
                    .isInstanceOf(BookingAccessDeniedException.class);
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        @DisplayName("Una reserva que no existe no se cancela")
        void rejectsUnknownBooking() {
            started(AVAILABLE, Duration.ofMinutes(10));

            assertThatThrownBy(() -> service.cancelBooking(404L, DRIVER)).isInstanceOf(BookingNotFoundException.class);
        }

        /* Idempotente: el front puede reintentar un pedido que se cortó sin preguntar antes. */
        @Test
        @DisplayName("Cancelar dos veces la misma reserva no es un error")
        void cancellingTwiceIsNotAnError() {
            started(AVAILABLE, Duration.ofMinutes(10));
            Booking booking = service.confirmBooking(
                    service.startHold(CONNECTOR, windowIn(2), DRIVER).id(), DRIVER);
            service.cancelBooking(booking.getId(), DRIVER);

            assertThatCode(() -> service.cancelBooking(booking.getId(), DRIVER)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Una reserva cuya ventana ya terminó no se cancela")
        void rejectsCancellingAPastBooking() {
            started(AVAILABLE, Duration.ofMinutes(10));
            Booking booking = service.confirmBooking(
                    service.startHold(CONNECTOR, windowIn(2), DRIVER).id(), DRIVER);

            clock.advance(Duration.ofHours(4));

            assertThatThrownBy(() -> service.cancelBooking(booking.getId(), DRIVER))
                    .isInstanceOf(InvalidBookingRequestException.class);
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        }
    }

    /** Los avisos que Reservas publica para Notificaciones (ECO-41, RF17). */
    @Nested
    @DisplayName("Avisos al conductor")
    class Notifying {

        @Test
        @DisplayName("Confirmar publica un aviso con los datos de la reserva")
        void confirmingPublishesTheBooking() {
            started(AVAILABLE, Duration.ofMinutes(10));
            TimeWindow window = windowIn(2);
            Booking booking = service.confirmBooking(
                    service.startHold(CONNECTOR, window, DRIVER).id(), DRIVER);

            assertThat(events.published()).singleElement().satisfies(event -> {
                assertThat(event.type()).isEqualTo(BookingEventType.BOOKING_CONFIRMED);
                assertThat(event.bookingId()).isEqualTo(booking.getId());
                assertThat(event.driverId()).isEqualTo(DRIVER);
                assertThat(event.connectorId()).isEqualTo(CONNECTOR);
                assertThat(event.windowStart()).isEqualTo(window.start());
                assertThat(event.windowEnd()).isEqualTo(window.end());
                assertThat(event.eventId()).isEqualTo("booking-" + booking.getId() + "-BOOKING_CONFIRMED");
            });
        }

        @Test
        @DisplayName("Cancelar publica un aviso, y cancelar dos veces no publica dos")
        void cancellingPublishesOnce() {
            started(AVAILABLE, Duration.ofMinutes(10));
            Booking booking = service.confirmBooking(
                    service.startHold(CONNECTOR, windowIn(2), DRIVER).id(), DRIVER);

            service.cancelBooking(booking.getId(), DRIVER);
            service.cancelBooking(booking.getId(), DRIVER);

            assertThat(events.published())
                    .extracting(BookingEvent::type)
                    .containsExactly(BookingEventType.BOOKING_CONFIRMED, BookingEventType.BOOKING_CANCELLED);
        }

        /* Un aviso de una reserva que no existe sería peor que ningún aviso. */
        @Test
        @DisplayName("Una confirmación rechazada no avisa nada")
        void rejectedConfirmationPublishesNothing() {
            started(AVAILABLE, Duration.ofMinutes(10));
            Hold hold = service.startHold(CONNECTOR, windowIn(5), DRIVER);
            clock.advance(Duration.ofMinutes(11));

            assertThatThrownBy(() -> service.confirmBooking(hold.id(), DRIVER))
                    .isInstanceOf(HoldExpiredException.class);
            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("Una cancelación rechazada no avisa nada")
        void rejectedCancellationPublishesNothing() {
            started(AVAILABLE, Duration.ofMinutes(10));
            Booking booking = service.confirmBooking(
                    service.startHold(CONNECTOR, windowIn(2), DRIVER).id(), DRIVER);

            assertThatThrownBy(() -> service.cancelBooking(booking.getId(), OTHER_DRIVER))
                    .isInstanceOf(BookingAccessDeniedException.class);
            assertThat(events.published())
                    .extracting(BookingEvent::type)
                    .containsExactly(BookingEventType.BOOKING_CONFIRMED);
        }
    }

    @Test
    @DisplayName("Cada conductor ve solo sus reservas, de la más próxima a la más lejana")
    void listsOnlyTheDriverOwnBookings() {
        started(AVAILABLE, Duration.ofMinutes(10));
        service.confirmBooking(service.startHold(CONNECTOR, windowIn(6), DRIVER).id(), DRIVER);
        service.confirmBooking(service.startHold(CONNECTOR, windowIn(2), DRIVER).id(), DRIVER);
        service.confirmBooking(
                service.startHold(CONNECTOR, windowIn(4), OTHER_DRIVER).id(), OTHER_DRIVER);

        List<Booking> mine = service.getDriverBookings(DRIVER);

        assertThat(mine).hasSize(2);
        assertThat(mine.get(0).getWindow().start())
                .isBefore(mine.get(1).getWindow().start());
        assertThat(mine).allMatch(booking -> booking.getDriverId().equals(DRIVER));
    }

    /*
     * La disponibilidad (ECO-33). El cálculo de huecos en sí está en FreeWindowsTest; acá se
     * prueba qué cuenta como ocupado y cómo se recorta el rango. El reloj arranca a las 12:00, así
     * que windowIn(2) es de 14 a 15.
     */
    @Nested
    @DisplayName("Disponibilidad de un conector")
    class Availability {

        /** De ahora a seis horas: un rango que no hay que recortar. */
        private List<TimeWindow> nextSixHours() {
            return service.getAvailability(
                    CONNECTOR, clock.instant(), clock.instant().plus(Duration.ofHours(6)));
        }

        private TimeWindow between(long fromHours, long toHours) {
            return new TimeWindow(
                    clock.instant().plus(Duration.ofHours(fromHours)),
                    clock.instant().plus(Duration.ofHours(toHours)));
        }

        @Test
        @DisplayName("Una reserva confirmada y una retención vigente ocupan; lo demás está libre")
        void bookingsAndHoldsAreOccupied() {
            started(AVAILABLE, Duration.ofMinutes(10));
            service.confirmBooking(
                    service.startHold(CONNECTOR, windowIn(1), DRIVER).id(), DRIVER);
            service.startHold(CONNECTOR, windowIn(3), OTHER_DRIVER);

            assertThat(nextSixHours()).containsExactly(between(0, 1), between(2, 3), between(4, 6));
        }

        @Test
        @DisplayName("Una reserva cancelada deja de ocupar")
        void cancelledBookingIsFree() {
            started(AVAILABLE, Duration.ofMinutes(10));
            Booking booking = service.confirmBooking(
                    service.startHold(CONNECTOR, windowIn(2), DRIVER).id(), DRIVER);

            service.cancelBooking(booking.getId(), DRIVER);

            assertThat(nextSixHours()).containsExactly(between(0, 6));
        }

        @Test
        @DisplayName("Una retención vencida deja de ocupar, aunque el reloj no la haya sacado")
        void expiredHoldIsFree() {
            started(AVAILABLE, Duration.ofMinutes(10));
            service.startHold(CONNECTOR, windowIn(2), DRIVER);

            clock.advance(Duration.ofMinutes(11));

            assertThat(nextSixHours()).containsExactly(between(0, 6));
        }

        @Test
        @DisplayName("Lo reservado en otro conector no ocupa este")
        void otherConnectorsDoNotCount() {
            started(AVAILABLE, Duration.ofMinutes(10));
            service.confirmBooking(
                    service.startHold(CONNECTOR + 1, windowIn(2), DRIVER).id(), DRIVER);

            assertThat(nextSixHours()).containsExactly(between(0, 6));
        }

        @Test
        @DisplayName("Con todo tomado, la lista viene vacía y no es un error")
        void fullyBookedIsAnEmptyList() {
            started(AVAILABLE, Duration.ofMinutes(10));
            service.confirmBooking(
                    service.startHold(CONNECTOR, windowIn(1), DRIVER).id(), DRIVER);

            assertThat(service.getAvailability(
                            CONNECTOR,
                            clock.instant().plus(Duration.ofHours(1)),
                            clock.instant().plus(Duration.ofHours(2))))
                    .isEmpty();
        }

        /* Fuera de servicio NO es "todo tomado": el conductor tiene que poder ver la diferencia. */
        @Test
        @DisplayName("Un conector fuera de servicio no devuelve lista vacía sino que se rechaza")
        void outOfServiceIsRejected() {
            started(id -> Optional.of(new ConnectorSnapshot(id, 1L, "OUT_OF_SERVICE")), Duration.ofMinutes(10));

            assertThatThrownBy(this::nextSixHours).isInstanceOf(ConnectorNotBookableException.class);
        }

        @Test
        @DisplayName("Un conector que no existe se rechaza")
        void unknownConnectorIsRejected() {
            started(id -> Optional.empty(), Duration.ofMinutes(10));

            assertThatThrownBy(this::nextSixHours).isInstanceOf(ConnectorNotFoundException.class);
        }

        @Test
        @DisplayName("Un rango que empezó antes de ahora se recorta a ahora")
        void rangeStartingInThePastIsClippedToNow() {
            started(AVAILABLE, Duration.ofMinutes(10));

            assertThat(service.getAvailability(
                            CONNECTOR,
                            clock.instant().minus(Duration.ofHours(3)),
                            clock.instant().plus(Duration.ofHours(1))))
                    .containsExactly(between(0, 1));
        }

        @Test
        @DisplayName("Un rango que se pasa del horizonte se recorta al horizonte")
        void rangeBeyondTheHorizonIsClipped() {
            started(AVAILABLE, Duration.ofMinutes(10));
            Instant horizon = clock.instant().plus(MAX_HORIZON);

            assertThat(service.getAvailability(
                            CONNECTOR, horizon.minus(Duration.ofHours(1)), horizon.plus(Duration.ofDays(5))))
                    .containsExactly(new TimeWindow(horizon.minus(Duration.ofHours(1)), horizon));
        }

        @Test
        @DisplayName("Un rango que ya terminó se rechaza, en vez de contestar que no hay huecos")
        void rangeInThePastIsRejected() {
            started(AVAILABLE, Duration.ofMinutes(10));

            assertThatThrownBy(() -> service.getAvailability(
                            CONNECTOR,
                            clock.instant().minus(Duration.ofHours(3)),
                            clock.instant().minus(Duration.ofHours(1))))
                    .isInstanceOf(InvalidBookingRequestException.class);
        }

        @Test
        @DisplayName("Un rango que empieza después del horizonte se rechaza")
        void rangeBeyondTheHorizonIsRejected() {
            started(AVAILABLE, Duration.ofMinutes(10));
            Instant afterHorizon = clock.instant().plus(MAX_HORIZON).plus(Duration.ofDays(1));

            assertThatThrownBy(() ->
                            service.getAvailability(CONNECTOR, afterHorizon, afterHorizon.plus(Duration.ofHours(2))))
                    .isInstanceOf(InvalidBookingRequestException.class);
        }

        @Test
        @DisplayName("Un rango invertido se rechaza")
        void invertedRangeIsRejected() {
            started(AVAILABLE, Duration.ofMinutes(10));

            assertThatThrownBy(() -> service.getAvailability(
                            CONNECTOR, clock.instant().plus(Duration.ofHours(2)), clock.instant()))
                    .isInstanceOf(InvalidBookingRequestException.class);
        }
    }
}
