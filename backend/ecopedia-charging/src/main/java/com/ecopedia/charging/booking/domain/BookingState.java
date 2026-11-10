package com.ecopedia.charging.booking.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Patrón State: lo que una {@link Booking} admite hacer depende del estado en que está.
 *
 * <p>Cada estado es una clase que implementa esta interfaz y redefine solo las transiciones que
 * admite; las demás caen en la implementación por defecto, que rechaza con
 * {@link BookingStateConflictException} (409 {@code BOOKING_STATE_CONFLICT}). Así la regla "desde
 * acá no se puede" no es un {@code if} repetido en cada método de la reserva, sino la ausencia del
 * método en la clase del estado: sumar una transición es redefinirla en el estado que la admite, y
 * sumar un estado es una clase nueva, sin tocar las otras.
 *
 * <p>Los estados no guardan nada: lo que cambia (el estado guardado, la seña, la ventana) es de la
 * reserva, que el estado recibe por parámetro y modifica con sus métodos de paquete. Por eso hay una
 * sola instancia de cada uno, la que tiene su constante de {@link BookingStatus}, y el estado se
 * sigue guardando en la misma columna de texto: no hizo falta migrar.
 *
 * <pre>
 *                  ┌──── cancelar · falla al habilitar ────► CANCELLED
 *                  ├──── no se presentó ───────────────────► NO_SHOW
 *   CONFIRMED ─────┼──── cerrar la carga ──────────────────► COMPLETED
 *                  └──── habilitar ──► IN_PROGRESS ─┬─ falla al habilitar ─► CANCELLED
 *                                                   └─ cerrar la carga ────► COMPLETED
 * </pre>
 *
 * Liquidar por falla (RF13) no cambia el estado: recorta la ventana y decide la seña, desde
 * {@code CONFIRMED} o {@code IN_PROGRESS}. {@code CANCELLED}, {@code NO_SHOW} y {@code COMPLETED}
 * son finales: no admiten nada.
 */
interface BookingState {

    /** El conductor cancela (ECO-32). */
    default void cancel(Booking booking, Instant now) {
        throw conflict(booking, "la cancelación");
    }

    /** El conductor no se presentó (RF09, ECO-57). */
    default void markNoShow(Booking booking) {
        throw conflict(booking, "la incomparecencia");
    }

    /** Se habilita la carga y la reserva se consume (RF11, ECO-60). */
    default void startCharging(Booking booking, Instant now) {
        throw conflict(booking, "la habilitación de la carga");
    }

    /** La carga no pudo iniciarse por una falla ajena al conductor (RF13, ECO-59). */
    default void failActivation(Booking booking) {
        throw conflict(booking, "la liberación por falla de habilitación");
    }

    /** La carga en curso se interrumpió por una falla ajena al conductor (RF13, ECO-59). */
    default void settleFaultedCharge(Booking booking, BigDecimal realCost, Instant now) {
        throw conflict(booking, "la liquidación por falla");
    }

    /** La sesión de carga terminó (RF12, RF14, ECO-55). */
    default void completeCharging(Booking booking, Instant stoppedAt) {
        throw conflict(booking, "el cierre de la carga");
    }

    private static BookingStateConflictException conflict(Booking booking, String transition) {
        return new BookingStateConflictException(booking.getId(), "en " + booking.getStatus(), transition);
    }
}
