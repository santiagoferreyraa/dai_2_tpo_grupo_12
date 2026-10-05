package com.ecopedia.charging.checkout.web;

import com.ecopedia.charging.booking.domain.BookingAccessDeniedException;
import com.ecopedia.charging.booking.domain.HoldExpiredException;
import com.ecopedia.charging.booking.domain.HoldNotFoundException;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.checkout.domain.GracePeriodNotAcceptedException;
import com.ecopedia.charging.checkout.domain.PaymentMethodRequiredException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones originadas en el proceso de Checkout a respuestas HTTP estandarizadas {@link ProblemDetail}.
 */
@RestControllerAdvice(basePackageClasses = CheckoutExceptionHandler.class)
public class CheckoutExceptionHandler {

    @ExceptionHandler(GracePeriodNotAcceptedException.class)
    public ProblemDetail handleGraceNotAccepted(GracePeriodNotAcceptedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(PaymentMethodRequiredException.class)
    public ProblemDetail handlePaymentMethodRequired(PaymentMethodRequiredException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(HoldNotFoundException.class)
    public ProblemDetail handleHoldNotFound(HoldNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(HoldExpiredException.class)
    public ProblemDetail handleHoldExpired(HoldExpiredException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, exception.getMessage());
    }

    @ExceptionHandler(BookingAccessDeniedException.class)
    public ProblemDetail handleAccessDenied(BookingAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(SlotUnavailableException.class)
    public ProblemDetail handleSlotUnavailable(SlotUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
