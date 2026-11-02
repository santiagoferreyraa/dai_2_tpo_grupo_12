package com.ecopedia.charging.session.web;

import com.ecopedia.charging.booking.domain.BookingNotFoundException;
import com.ecopedia.charging.booking.domain.ConnectorNotBookableException;
import com.ecopedia.charging.booking.domain.ConnectorNotFoundException;
import com.ecopedia.charging.booking.domain.InvalidBookingRequestException;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.checkout.domain.PaymentMethodInvalidException;
import com.ecopedia.charging.session.domain.PhysicalChargerRejectedException;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = SessionController.class)
public class SessionExceptionHandler {

    @ExceptionHandler(SlotUnavailableException.class)
    public ProblemDetail handleSlotUnavailable(SlotUnavailableException ex) {
        return withCode(HttpStatus.CONFLICT, ex.getMessage(), "SLOT_UNAVAILABLE");
    }

    @ExceptionHandler(ConnectorNotBookableException.class)
    public ProblemDetail handleConnectorNotBookable(ConnectorNotBookableException ex) {
        return withCode(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), "CONNECTOR_NOT_BOOKABLE");
    }

    @ExceptionHandler(ConnectorNotFoundException.class)
    public ProblemDetail handleConnectorNotFound(ConnectorNotFoundException ex) {
        return withCode(HttpStatus.NOT_FOUND, ex.getMessage(), "CONNECTOR_NOT_FOUND");
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ProblemDetail handleBookingNotFound(BookingNotFoundException ex) {
        return withCode(HttpStatus.NOT_FOUND, ex.getMessage(), "BOOKING_NOT_FOUND");
    }

    @ExceptionHandler(PaymentMethodInvalidException.class)
    public ProblemDetail handlePaymentInvalid(PaymentMethodInvalidException ex) {
        return withCode(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), "PAYMENT_METHOD_INVALID");
    }

    @ExceptionHandler(PhysicalChargerRejectedException.class)
    public ProblemDetail handleChargerRejected(PhysicalChargerRejectedException ex) {
        return withCode(HttpStatus.CONFLICT, ex.getMessage(), "CHARGER_REJECTED");
    }

    @ExceptionHandler(PhysicalChargerUnavailableException.class)
    public ProblemDetail handleChargerUnavailable(PhysicalChargerUnavailableException ex) {
        return withCode(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), "CHARGER_UNAVAILABLE");
    }

    @ExceptionHandler(InvalidBookingRequestException.class)
    public ProblemDetail handleInvalidBooking(InvalidBookingRequestException ex) {
        return withCode(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_REQUEST");
    }

    private static ProblemDetail withCode(HttpStatus status, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
