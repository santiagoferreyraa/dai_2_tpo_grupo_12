package com.ecopedia.charging.checkout.web;

import com.ecopedia.charging.booking.domain.DriverAlreadyBookedException;
import com.ecopedia.charging.booking.domain.HoldExpiredException;
import com.ecopedia.charging.booking.domain.HoldNotFoundException;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.checkout.domain.CardDeclinedException;
import com.ecopedia.charging.checkout.domain.DepositPaymentUnavailableException;
import com.ecopedia.charging.checkout.domain.GracePeriodNotAcceptedException;
import com.ecopedia.charging.checkout.domain.IdempotencyKeyRequiredException;
import com.ecopedia.charging.checkout.domain.IdempotencyKeyReusedException;
import com.ecopedia.charging.checkout.domain.PaymentMethodInvalidException;
import com.ecopedia.charging.checkout.domain.PaymentMethodRequiredException;
import com.ecopedia.charging.checkout.domain.PaymentMethodsUnavailableException;
import com.ecopedia.charging.checkout.domain.PricingUnavailableException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones originadas en el proceso de Checkout a respuestas HTTP estandarizadas {@link ProblemDetail}.
 *
 * <p><b>Los rechazos propios del checkout llevan un {@code code}.</b> Dos de ellos son 400 y el front
 * tiene que distinguirlos —"cargá una tarjeta" no es "aceptá la tolerancia"—, y el {@code detail} es texto
 * para el log, no para el conductor: el front elige sus propias palabras según el código.
 */
@RestControllerAdvice(basePackageClasses = CheckoutExceptionHandler.class)
public class CheckoutExceptionHandler {

    @ExceptionHandler(GracePeriodNotAcceptedException.class)
    public ProblemDetail handleGraceNotAccepted(GracePeriodNotAcceptedException exception) {
        return withCode(HttpStatus.BAD_REQUEST, exception.getMessage(), "GRACE_PERIOD_NOT_ACCEPTED");
    }

    @ExceptionHandler(PaymentMethodRequiredException.class)
    public ProblemDetail handlePaymentMethodRequired(PaymentMethodRequiredException exception) {
        return withCode(HttpStatus.BAD_REQUEST, exception.getMessage(), "PAYMENT_METHOD_REQUIRED");
    }

    /** Pagos no contestó: 503, igual que Reservas con core caído. La retención sigue en pie. */
    @ExceptionHandler(PaymentMethodsUnavailableException.class)
    public ProblemDetail handlePaymentMethodsUnavailable(PaymentMethodsUnavailableException exception) {
        return withCode(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), "PAYMENT_CHECK_UNAVAILABLE");
    }

    /** Sin {@code Idempotency-Key}, o con una que no tiene la forma esperada (QA-29). */
    @ExceptionHandler(IdempotencyKeyRequiredException.class)
    public ProblemDetail handleKeyRequired(IdempotencyKeyRequiredException exception) {
        return withCode(HttpStatus.BAD_REQUEST, exception.getMessage(), "IDEMPOTENCY_KEY_REQUIRED");
    }

    /** La clave ya nombra otro pedido: 422, la convención para una clave reusada con otro contenido. */
    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ProblemDetail handleKeyReused(IdempotencyKeyReusedException exception) {
        return withCode(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), "IDEMPOTENCY_KEY_REUSED");
    }

    /**
     * La pasarela rechazó la seña: 402, que es exactamente "falta el pago". La retención sigue en pie,
     * así que el conductor puede probar con otra tarjeta sin volver a elegir horario.
     */
    @ExceptionHandler(CardDeclinedException.class)
    public ProblemDetail handleCardDeclined(CardDeclinedException exception) {
        return withCode(HttpStatus.PAYMENT_REQUIRED, exception.getMessage(), "CARD_DECLINED");
    }

    /** La tarjeta elegida no sirve: la dio de baja en otra pestaña, venció, no es suya. */
    @ExceptionHandler(PaymentMethodInvalidException.class)
    public ProblemDetail handlePaymentMethodInvalid(PaymentMethodInvalidException exception) {
        return withCode(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), "PAYMENT_METHOD_INVALID");
    }

    /** Pagos o la pasarela no contestaron al cobrar. El reintento con la misma clave no cobra dos veces. */
    @ExceptionHandler(DepositPaymentUnavailableException.class)
    public ProblemDetail handlePaymentUnavailable(DepositPaymentUnavailableException exception) {
        return withCode(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), "PAYMENT_UNAVAILABLE");
    }

    /** Tarificación no contestó: no se sabe cuánto es la seña, y sin saberlo no se confirma. */
    @ExceptionHandler(PricingUnavailableException.class)
    public ProblemDetail handlePricingUnavailable(PricingUnavailableException exception) {
        return withCode(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), "PRICING_UNAVAILABLE");
    }

    @ExceptionHandler(HoldNotFoundException.class)
    public ProblemDetail handleHoldNotFound(HoldNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(HoldExpiredException.class)
    public ProblemDetail handleHoldExpired(HoldExpiredException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, exception.getMessage());
    }

    @ExceptionHandler(SlotUnavailableException.class)
    public ProblemDetail handleSlotUnavailable(SlotUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    /**
     * El conductor quedó con una reserva en curso o con otra que se cruza mientras confirmaba.
     *
     * <p>Mismo 409 y mismo {@code code} que en el handler de Reservas: es la misma regla, y el front
     * la lee igual venga de retener o de confirmar.
     */
    @ExceptionHandler(DriverAlreadyBookedException.class)
    public ProblemDetail handleDriverAlreadyBooked(DriverAlreadyBookedException exception) {
        return withCode(HttpStatus.CONFLICT, exception.getMessage(), exception.code());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    private static ProblemDetail withCode(HttpStatus status, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
