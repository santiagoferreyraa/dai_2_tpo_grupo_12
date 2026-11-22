package com.ecopedia.charging.booking.domain;

/**
 * {@code IN_PROGRESS}: la carga está habilitada. Solo admite lo de {@link ActiveBookingState}: cerrar
 * la carga, o liberarla o liquidarla por una falla. Ya no se cancela ni se marca como incomparecencia,
 * porque el conductor se presentó.
 */
final class InProgressBookingState extends ActiveBookingState {}
