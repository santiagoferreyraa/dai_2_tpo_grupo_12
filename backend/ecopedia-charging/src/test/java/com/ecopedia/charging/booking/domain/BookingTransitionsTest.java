package com.ecopedia.charging.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * La máquina de estados de la reserva (QA-26): cada transición inválida se rechaza con
 * {@link BookingStateConflictException}, que la API responde con 409, y la reserva queda como
 * estaba.
 *
 * <p>Los estados: {@code CONFIRMED} es el único del que se sale; {@code CANCELLED} y
 * {@code NO_SHOW} son finales. Y una {@code CONFIRMED} cuya ventana ya terminó no se cancela. Las
 * transiciones son las cuatro que cambian el estado: cancelar, la incomparecencia, y liberar o
 * liquidar por falla (RF13, ECO-59). Cancelar una ya cancelada no está acá porque no es inválido:
 * es idempotente, y lo resuelve el servicio antes de llegar a la reserva ({@code BookingApiTest}).
 *
 * <p>Las reservas tienen seña cobrada a propósito: así se ve también que el rechazo no toca la
 * plata.
 */
class BookingTransitionsTest {

    /** El "ahora" de todas las pruebas. La ventana de las reservas vigentes empieza 2 h después. */
    private static final Instant NOW = Instant.parse("2026-11-10T15:00:00Z");

    @ParameterizedTest(name = "{0} rechaza {2}")
    @MethodSource("invalidTransitions")
    @DisplayName("Una transición inválida se rechaza con 409 y la reserva queda como estaba")
    void invalidTransitionIsRejected(
            String state, Function<Instant, Booking> inState, String transition, BiConsumer<Booking, Instant> apply) {
        Booking booking = inState.apply(NOW);
        BookingStatus statusBefore = booking.getStatus();
        DepositStatus depositBefore = booking.getDepositStatus();
        TimeWindow windowBefore = booking.getWindow();

        assertThatThrownBy(() -> apply.accept(booking, NOW)).isInstanceOf(BookingStateConflictException.class);

        assertThat(booking.getStatus()).isEqualTo(statusBefore);
        assertThat(booking.getDepositStatus()).isEqualTo(depositBefore);
        assertThat(booking.getWindow()).isEqualTo(windowBefore);
    }

    /**
     * El control: las mismas cuatro transiciones, desde una reserva confirmada y vigente, valen. Sin
     * esto, el test de arriba pasaría también con una reserva que lo rechaza todo.
     */
    @ParameterizedTest(name = "CONFIRMED admite {0} y pasa a {2}")
    @MethodSource("transitions")
    @DisplayName("Desde una reserva confirmada y vigente, las cuatro transiciones valen")
    void confirmedBookingAcceptsEveryTransition(
            String transition, BiConsumer<Booking, Instant> apply, BookingStatus expected) {
        Booking booking = confirmed(NOW);

        apply.accept(booking, NOW);

        assertThat(booking.getStatus()).isEqualTo(expected);
    }

    static Stream<Arguments> invalidTransitions() {
        Stream<Arguments> finalStates = Stream.of(
                        Arguments.of("CANCELLED", (Function<Instant, Booking>) BookingTransitionsTest::cancelled),
                        Arguments.of("NO_SHOW", (Function<Instant, Booking>) BookingTransitionsTest::noShow))
                .flatMap(state -> transitions()
                        .map(transition -> Arguments.of(
                                state.get()[0],
                                state.get()[1],
                                transition.get()[0],
                                transition.get()[1])));
        Stream<Arguments> ended = Stream.of(Arguments.of(
                "CONFIRMED con la ventana terminada",
                (Function<Instant, Booking>) BookingTransitionsTest::endedWindow,
                "cancelar",
                (BiConsumer<Booking, Instant>) Booking::cancel));
        return Stream.concat(finalStates, ended);
    }

    /**
     * Las cuatro transiciones y el estado al que llevan desde {@code CONFIRMED}. Liquidar por falla
     * deja la reserva confirmada (con la ventana recortada y el reintegro de la diferencia).
     */
    static Stream<Arguments> transitions() {
        return Stream.of(
                Arguments.of("cancelar", (BiConsumer<Booking, Instant>) Booking::cancel, BookingStatus.CANCELLED),
                Arguments.of(
                        "la incomparecencia",
                        (BiConsumer<Booking, Instant>) (b, now) -> b.markNoShow(),
                        BookingStatus.NO_SHOW),
                Arguments.of(
                        "liberar por falla de habilitación",
                        (BiConsumer<Booking, Instant>) (b, now) -> b.failActivation(),
                        BookingStatus.CANCELLED),
                Arguments.of(
                        "liquidar por falla",
                        (BiConsumer<Booking, Instant>) (b, now) -> b.settleFaultedCharge(new BigDecimal("3000"), now),
                        BookingStatus.CONFIRMED));
    }

    /** Confirmada, con la ventana dentro de 2 h y una seña de $ 8.000 devolvible hasta 5 h antes. */
    private static Booking confirmed(Instant now) {
        TimeWindow window = new TimeWindow(now.plus(Duration.ofHours(2)), now.plus(Duration.ofHours(3)));
        return new Booking(
                12L,
                77L,
                window,
                now.minus(Duration.ofDays(1)),
                new BookingDeposit(new BigDecimal("8000"), 501L, "4242"),
                window.start().minus(Duration.ofHours(5)),
                UUID.randomUUID(),
                "clave-" + UUID.randomUUID());
    }

    private static Booking cancelled(Instant now) {
        Booking booking = confirmed(now);
        booking.cancel(now);
        return booking;
    }

    private static Booking noShow(Instant now) {
        Booking booking = confirmed(now);
        booking.markNoShow();
        return booking;
    }

    /** Confirmada, pero con la ventana entera en el pasado. */
    private static Booking endedWindow(Instant now) {
        TimeWindow window = new TimeWindow(now.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(2)));
        return new Booking(
                12L,
                77L,
                window,
                now.minus(Duration.ofDays(1)),
                new BookingDeposit(new BigDecimal("8000"), 501L, "4242"),
                window.start().minus(Duration.ofHours(5)),
                UUID.randomUUID(),
                "clave-" + UUID.randomUUID());
    }
}
