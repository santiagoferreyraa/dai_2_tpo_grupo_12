package com.ecopedia.charging.booking.domain;

/**
 * {@code CANCELLED}: final. No admite ninguna transición. Cancelar una ya cancelada no llega acá:
 * el servicio lo resuelve antes, porque cancelar es idempotente.
 */
final class CancelledBookingState implements BookingState {}
