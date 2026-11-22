package com.ecopedia.integration.payment.domain;

/**
 * Resultado de dominio de la validación previa de medios de pago y deuda (RF11, RF14, ECO-54).
 *
 * @param valid Indica si el conductor está habilitado para iniciar una sesión de carga.
 * @param reason Explicación legible del resultado para logs y auditoría.
 * @param errorCode Código atómico de error si la validación no fue aprobada.
 */
public record PaymentValidationResult(boolean valid, String reason, ValidationErrorCode errorCode) {

    public enum ValidationErrorCode {
        NONE,
        NO_PAYMENT_METHOD,
        CARD_EXPIRED,
        CARD_DECLINED,
        DRIVER_HAS_DEBT,
        GATEWAY_ERROR
    }

    public static PaymentValidationResult ok() {
        return new PaymentValidationResult(true, "Tarjeta válida y cobrable sin deuda", ValidationErrorCode.NONE);
    }

    public static PaymentValidationResult rejected(String reason, ValidationErrorCode code) {
        return new PaymentValidationResult(false, reason, code);
    }
}
