package com.ecopedia.charging.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * La máquina de estados de la reserva (patrón State, QA-26): la tabla completa, cada estado contra
 * cada transición. Lo que el estado admite lleva al estado esperado; lo que no admite se rechaza con
 * {@link BookingStateConflictException}, que la API responde con 409, y la reserva queda como estaba.
 *
 * <pre>
 *                cancelar   no-show    habilitar    falla habil.  liquidar falla  cerrar carga
 *   CONFIRMED    CANCELLED  NO_SHOW    IN_PROGRESS  CANCELLED     CONFIRMED       COMPLETED
 *   IN_PROGRESS  409        409        409          CANCELLED     IN_PROGRESS     COMPLETED
 *   CANCELLED    409        409        409          409           409             409
 *   NO_SHOW      409        409        409          409           409             409
 *   COMPLETED    409        409        409          409           409             409
 * </pre>
 *
 * Además, una {@code CONFIRMED} cuya ventana ya terminó no se cancela. Cancelar una ya cancelada no
 * está acá como inválido para el usuario: es idempotente y lo resuelve el servicio antes de llegar a la
 * reserva ({@code BookingApiTest}); la reserva en sí lo rechaza.
 *
 * <p>Las reservas tienen seña cobrada a propósito: así se ve también que el rechazo no toca la plata.
 */
class BookingTransitionsTest {

    /** El "ahora" de todas las pruebas. La ventana de las reservas vigentes empieza 2 h después. */
    private static final Instant NOW = Instant.parse("2026-11-10T15:00:00Z");

    /** Las seis transiciones, en el orden de las columnas de la tabla. */
    private enum Transition {
        CANCEL("cancelar", Booking::cancel),
        NO_SHOW("la incomparecencia", (b, now) -> b.markNoShow()),
        START("habilitar la carga", Booking::startCharging),
        FAIL_ACTIVATION("liberar por falla de habilitación", (b, now) -> b.failActivation()),
        SETTLE_FAULT("liquidar por falla", (b, now) -> b.settleFaultedCharge(new BigDecimal("3000"), now)),
        COMPLETE("cerrar la carga", Booking::completeCharging);

        private final String label;
        private final BiConsumer<Booking, Instant> apply;

        Transition(String label, BiConsumer<Booking, Instant> apply) {
            this.label = label;
            this.apply = apply;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** La tabla: a qué estado lleva cada transición admitida. Lo que no está, es 409. */
    private static final Map<BookingStatus, Map<Transition, BookingStatus>> ALLOWED =
            new EnumMap<>(BookingStatus.class);

    static {
        Map<Transition, BookingStatus> fromConfirmed = new EnumMap<>(Transition.class);
        fromConfirmed.put(Transition.CANCEL, BookingStatus.CANCELLED);
        fromConfirmed.put(Transition.NO_SHOW, BookingStatus.NO_SHOW);
        fromConfirmed.put(Transition.START, BookingStatus.IN_PROGRESS);
        fromConfirmed.put(Transition.FAIL_ACTIVATION, BookingStatus.CANCELLED);
        fromConfirmed.put(Transition.SETTLE_FAULT, BookingStatus.CONFIRMED);
        fromConfirmed.put(Transition.COMPLETE, BookingStatus.COMPLETED);
        ALLOWED.put(BookingStatus.CONFIRMED, fromConfirmed);

        Map<Transition, BookingStatus> fromInProgress = new EnumMap<>(Transition.class);
        fromInProgress.put(Transition.FAIL_ACTIVATION, BookingStatus.CANCELLED);
        fromInProgress.put(Transition.SETTLE_FAULT, BookingStatus.IN_PROGRESS);
        fromInProgress.put(Transition.COMPLETE, BookingStatus.COMPLETED);
        ALLOWED.put(BookingStatus.IN_PROGRESS, fromInProgress);

        ALLOWED.put(BookingStatus.CANCELLED, Map.of());
        ALLOWED.put(BookingStatus.NO_SHOW, Map.of());
        ALLOWED.put(BookingStatus.COMPLETED, Map.of());
    }

    @ParameterizedTest(name = "{0} admite {1} y pasa a {2}")
    @MethodSource("allowedCells")
    @DisplayName("Una transición que el estado admite lleva al estado de la tabla")
    void allowedTransitionReachesExpectedState(BookingStatus from, Transition transition, BookingStatus expected) {
        Booking booking = inStatus(from);

        transition.apply.accept(booking, NOW);

        assertThat(booking.getStatus()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} rechaza {1}")
    @MethodSource("rejectedCells")
    @DisplayName("Una transición que el estado no admite se rechaza con 409 y la reserva queda como estaba")
    void rejectedTransitionLeavesBookingUntouched(BookingStatus from, Transition transition) {
        Booking booking = inStatus(from);
        DepositStatus depositBefore = booking.getDepositStatus();
        TimeWindow windowBefore = booking.getWindow();

        assertThatThrownBy(() -> transition.apply.accept(booking, NOW))
                .isInstanceOf(BookingStateConflictException.class);

        assertThat(booking.getStatus()).isEqualTo(from);
        assertThat(booking.getDepositStatus()).isEqualTo(depositBefore);
        assertThat(booking.getWindow()).isEqualTo(windowBefore);
    }

    /** La tabla cubre todos los estados: un estado nuevo sin fila haría fallar esto. */
    @Test
    @DisplayName("La tabla tiene una fila por cada estado de la reserva")
    void tableCoversEveryStatus() {
        assertThat(ALLOWED.keySet()).containsExactlyInAnyOrder(BookingStatus.values());
    }

    @Test
    @DisplayName("Una reserva confirmada cuya ventana ya terminó no se cancela: 409")
    void confirmedWithEndedWindowCannotBeCancelled() {
        Booking booking = withWindow(NOW.minus(Duration.ofHours(3)), NOW.minus(Duration.ofHours(2)));

        assertThatThrownBy(() -> booking.cancel(NOW)).isInstanceOf(BookingStateConflictException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getDepositStatus()).isEqualTo(DepositStatus.CHARGED);
    }

    @Test
    @DisplayName("La seña según cómo terminó: a tiempo se devuelve, tarde o sin presentarse queda para el operador")
    void depositFollowsTheTransition() {
        // Empieza en 10 h: se devuelve si se cancela hasta 5 h después de NOW, inclusive.
        Instant start = NOW.plus(Duration.ofHours(10));
        Booking onTime = withWindow(start, start.plus(Duration.ofHours(1)));
        onTime.cancel(NOW.plus(Duration.ofHours(5)));
        assertThat(onTime.getDepositStatus()).isEqualTo(DepositStatus.REFUND_PENDING);

        Booking late = withWindow(start, start.plus(Duration.ofHours(1)));
        late.cancel(NOW.plus(Duration.ofHours(5)).plusSeconds(1));
        assertThat(late.getDepositStatus()).isEqualTo(DepositStatus.FORFEITED);

        Booking noShow = inStatus(BookingStatus.CONFIRMED);
        noShow.markNoShow();
        assertThat(noShow.getDepositStatus()).isEqualTo(DepositStatus.FORFEITED);

        Booking failed = inStatus(BookingStatus.IN_PROGRESS);
        failed.failActivation();
        assertThat(failed.getDepositStatus()).isEqualTo(DepositStatus.REFUND_PENDING);
        assertThat(failed.getDepositRefundAmount()).isEqualByComparingTo("8000");

        Booking faulted = inStatus(BookingStatus.IN_PROGRESS);
        faulted.settleFaultedCharge(new BigDecimal("3000"), NOW);
        assertThat(faulted.getDepositRefundAmount()).isEqualByComparingTo("5000");
    }

    static Stream<Arguments> allowedCells() {
        return ALLOWED.entrySet().stream().flatMap(row -> row.getValue().entrySet().stream()
                .map(cell -> Arguments.of(row.getKey(), cell.getKey(), cell.getValue())));
    }

    static Stream<Arguments> rejectedCells() {
        return ALLOWED.entrySet().stream().flatMap(row -> Stream.of(Transition.values())
                .filter(transition -> !row.getValue().containsKey(transition))
                .map(transition -> Arguments.of(row.getKey(), transition)));
    }

    /** Una reserva llevada a ese estado por el camino real, sin tocar el campo. */
    private static Booking inStatus(BookingStatus status) {
        Booking booking = withWindow(NOW.plus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(3)));
        switch (status) {
            case CONFIRMED -> {
                // Recién confirmada.
            }
            case IN_PROGRESS -> booking.startCharging(NOW);
            case CANCELLED -> booking.cancel(NOW);
            case NO_SHOW -> booking.markNoShow();
            case COMPLETED -> {
                booking.startCharging(NOW);
                booking.completeCharging(NOW);
            }
        }
        assertThat(booking.getStatus()).isEqualTo(status);
        return booking;
    }

    /** Confirmada, con una seña de $ 8.000 devolvible hasta 5 h antes del inicio. */
    private static Booking withWindow(Instant start, Instant end) {
        TimeWindow window = new TimeWindow(start, end);
        return new Booking(
                12L,
                77L,
                window,
                NOW.minus(Duration.ofDays(1)),
                new BookingDeposit(new BigDecimal("8000"), 501L, "4242"),
                window.start().minus(Duration.ofHours(5)),
                UUID.randomUUID(),
                "clave-" + UUID.randomUUID());
    }
}
