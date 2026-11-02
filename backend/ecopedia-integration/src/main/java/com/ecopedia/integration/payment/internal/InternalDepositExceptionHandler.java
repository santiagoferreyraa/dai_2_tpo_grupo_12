package com.ecopedia.integration.payment.internal;

import com.ecopedia.integration.payment.domain.DepositChargeNotFoundException;
import com.ecopedia.integration.payment.domain.DepositNotRefundableException;
import com.ecopedia.integration.payment.domain.IdempotencyKeyReusedException;
import com.ecopedia.integration.payment.domain.NoUsablePaymentMethodException;
import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Los resultados de la seña que no son un cobro, con un {@code code} estable.
 *
 * <p>Acotado a este paquete, como el de RedEléctrica, y con el mismo cuerpo: solo el código. Quien
 * llama es otro artefacto, que decide por él; el detalle queda en el log de acá.
 */
@RestControllerAdvice(basePackageClasses = InternalDepositExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalDepositExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(InternalDepositExceptionHandler.class);

    @ExceptionHandler(NoUsablePaymentMethodException.class)
    public ProblemDetail handleNoCard(NoUsablePaymentMethodException exception) {
        log.info("Seña sin tarjeta con la que cobrar: {}", exception.getMessage());
        return code(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_METHOD_INVALID");
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ProblemDetail handleKeyReused(IdempotencyKeyReusedException exception) {
        return code(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED");
    }

    @ExceptionHandler(DepositChargeNotFoundException.class)
    public ProblemDetail handleNotFound(DepositChargeNotFoundException exception) {
        return code(HttpStatus.NOT_FOUND, "DEPOSIT_CHARGE_NOT_FOUND");
    }

    @ExceptionHandler(DepositNotRefundableException.class)
    public ProblemDetail handleNotRefundable(DepositNotRefundableException exception) {
        return code(HttpStatus.CONFLICT, "DEPOSIT_NOT_REFUNDABLE");
    }

    /**
     * Un reintegro que la pasarela no aceptó (el adaptador lo señala así). No se arregla
     * reintentando, y por eso no es un 503: deja ERROR, porque es plata del conductor que no volvió.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleGatewayRefused(IllegalArgumentException exception) {
        log.error("La pasarela no aceptó una operación de seña: {}", exception.getMessage());
        return code(HttpStatus.CONFLICT, "DEPOSIT_NOT_REFUNDABLE");
    }

    @ExceptionHandler(PaymentGatewayUnavailableException.class)
    public ProblemDetail handleUnavailable(PaymentGatewayUnavailableException exception) {
        return code(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_GATEWAY_UNAVAILABLE");
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        // Un id que no es número: sin esto, lo atraparía el de IllegalArgumentException por su causa.
        TypeMismatchException.class
    })
    public ProblemDetail handleInvalidBody(Exception exception) {
        return code(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    /** Problem Details con el {@code code} estable que lee charging (QA-31). */
    private static ProblemDetail code(HttpStatus status, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setProperty("code", code);
        return problem;
    }
}
