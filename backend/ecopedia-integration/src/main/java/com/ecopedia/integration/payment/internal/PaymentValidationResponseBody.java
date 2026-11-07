package com.ecopedia.integration.payment.internal;

import com.ecopedia.integration.payment.domain.PaymentValidationResult;

/**
 * Respuesta interna de validación exitosa de medios de pago (ECO-54).
 */
public record PaymentValidationResponseBody(boolean valid, String reason, String code) {

    public static PaymentValidationResponseBody from(PaymentValidationResult result) {
        return new PaymentValidationResponseBody(
                result.valid(), result.reason(), result.errorCode().name());
    }
}
