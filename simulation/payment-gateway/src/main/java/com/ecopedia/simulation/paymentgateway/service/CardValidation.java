package com.ecopedia.simulation.paymentgateway.service;

import com.ecopedia.simulation.paymentgateway.domain.PaymentStatus;

/**
 * El resultado de validar una tarjeta sin cobrarle nada (RF11).
 *
 * @param status {@code APPROVED} si se le puede cobrar, {@code REJECTED} si no.
 * @param statusDetail el motivo, con los mismos códigos que un cobro rechazado.
 */
public record CardValidation(PaymentStatus status, String statusDetail) {}
