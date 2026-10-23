package com.ecopedia.integration.payment.domain;

/**
 * Si a una tarjeta se le puede cobrar, según la pasarela (RF11). No movió plata.
 *
 * @param chargeable si la pasarela dice que sí.
 * @param statusDetail el motivo cuando dice que no ({@code cc_rejected_other_reason}...).
 */
public record CardValidation(boolean chargeable, String statusDetail) {}
