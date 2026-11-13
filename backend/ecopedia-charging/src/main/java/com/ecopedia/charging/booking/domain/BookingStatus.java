package com.ecopedia.charging.booking.domain;

/**
 * Estado de una reserva guardada, y la clase que decide qué admite en ese estado (patrón State, ver
 * {@link BookingState}).
 *
 * <p>La columna es texto y no un número: agregar un estado no reinterpreta los que ya están
 * guardados. Cada constante lleva la única instancia de su estado, así que la reserva leída de la base
 * ya sabe qué admite sin que nadie lo traduzca.
 */
public enum BookingStatus {
    CONFIRMED(new ConfirmedBookingState()),
    CANCELLED(new CancelledBookingState()),
    /**
     * El conductor no se presentó: pasó la tolerancia desde el inicio y la reserva seguía
     * confirmada (RF09, ECO-57). Libera el conector por el resto de la ventana y al conductor.
     */
    NO_SHOW(new NoShowBookingState()),
    /**
     * La reserva fue consumida al iniciar la sesión de carga (RF11, ECO-44, ECO-60).
     * Pasa a este estado dentro de la transacción declarativa de habilitación.
     */
    IN_PROGRESS(new InProgressBookingState()),
    /**
     * La sesión de carga finalizó normalmente (RF12, RF14, ECO-55).
     */
    COMPLETED(new CompletedBookingState());

    /** Sin estado propio: las enumeraciones se serializan por nombre, y esto no viaja. */
    private final transient BookingState behavior;

    BookingStatus(BookingState behavior) {
        this.behavior = behavior;
    }

    BookingState behavior() {
        return behavior;
    }
}
