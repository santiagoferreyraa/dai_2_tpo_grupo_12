package com.ecopedia.charging.session.web;

import com.ecopedia.charging.booking.domain.ActivationTooEarlyException;
import com.ecopedia.charging.booking.domain.BookingNotFoundException;
import com.ecopedia.charging.booking.domain.BookingStateConflictException;
import com.ecopedia.charging.booking.domain.ConnectorNotBookableException;
import com.ecopedia.charging.booking.domain.ConnectorNotFoundException;
import com.ecopedia.charging.booking.domain.DriverAlreadyBookedException;
import com.ecopedia.charging.booking.domain.InvalidBookingRequestException;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import com.ecopedia.charging.checkout.domain.PaymentMethodInvalidException;
import com.ecopedia.charging.checkout.domain.PaymentMethodsUnavailableException;
import com.ecopedia.charging.session.domain.PhysicalChargerRejectedException;
import com.ecopedia.charging.session.domain.PhysicalChargerUnavailableException;
import com.ecopedia.charging.session.domain.SessionNotFoundException;
import com.ecopedia.charging.web.ApiProblems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = SessionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SessionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SessionExceptionHandler.class);

    @ExceptionHandler(SlotUnavailableException.class)
    public ProblemDetail handleSlotUnavailable(SlotUnavailableException ex) {
        return withCode(HttpStatus.CONFLICT, ex.getMessage(), "SLOT_UNAVAILABLE");
    }

    @ExceptionHandler(DriverAlreadyBookedException.class)
    public ProblemDetail handleDriverAlreadyBooked(DriverAlreadyBookedException ex) {
        return withCode(HttpStatus.CONFLICT, ex.getMessage(), "DRIVER_ALREADY_BOOKED");
    }

    @ExceptionHandler(ConnectorNotBookableException.class)
    public ProblemDetail handleConnectorNotBookable(ConnectorNotBookableException ex) {
        return withCode(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), "CONNECTOR_NOT_BOOKABLE");
    }

    @ExceptionHandler({ConnectorNotFoundException.class, BookingNotFoundException.class, SessionNotFoundException.class
    })
    public ProblemDetail handleNotFound(RuntimeException ex) {
        // Un único 404 para todo (QA-31, SEC-06): el id y el motivo quedan solo en el log.
        log.warn("Recurso no encontrado en sesión de carga: {}", ex.getMessage());
        return ApiProblems.notFound();
    }

    @ExceptionHandler(PaymentMethodInvalidException.class)
    public ProblemDetail handlePaymentInvalid(PaymentMethodInvalidException ex) {
        return withCode(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), "PAYMENT_METHOD_INVALID");
    }

    @ExceptionHandler(PaymentMethodsUnavailableException.class)
    public ProblemDetail handlePaymentMethodsUnavailable(PaymentMethodsUnavailableException ex) {
        return withCode(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), "PAYMENT_CHECK_UNAVAILABLE");
    }

    @ExceptionHandler(PhysicalChargerRejectedException.class)
    public ProblemDetail handleChargerRejected(PhysicalChargerRejectedException ex) {
        log.warn("El cargador físico rechazó la activación: {}", ex.getMessage(), ex.getCause());
        return withCode(HttpStatus.CONFLICT, ApiProblems.CHARGER_REJECTED_DETAIL, "CHARGER_REJECTED");
    }

    @ExceptionHandler(PhysicalChargerUnavailableException.class)
    public ProblemDetail handleChargerUnavailable(PhysicalChargerUnavailableException ex) {
        log.warn("El cargador físico no está disponible: {}", ex.getMessage(), ex.getCause());
        return withCode(HttpStatus.SERVICE_UNAVAILABLE, ApiProblems.CHARGER_UNAVAILABLE_DETAIL, "CHARGER_UNAVAILABLE");
    }

    /**
     * Habilitar una reserva que ya no está confirmada, o cerrar la carga de una que ya se cerró: la
     * reserva lo rechaza por su estado (patrón State). Mismo 409 que en Reservas; el id y el estado,
     * solo al log. Sin esto caía en el manejador general y salía como 500.
     */
    @ExceptionHandler(BookingStateConflictException.class)
    public ProblemDetail handleStateConflict(BookingStateConflictException ex) {
        log.info("Transición de reserva rechazada en sesión de carga (409): {}", ex.getMessage());
        return withCode(HttpStatus.CONFLICT, ApiProblems.STATE_CONFLICT_DETAIL, BookingStateConflictException.CODE);
    }

    /**
     * Habilitar antes del rango de la reserva (desde 15 minutos antes del turno): 409 con su propio
     * {@code code} y la hora desde la que se puede, para que el front diga "podés cargar desde las
     * HH:MM". Es la hora de la reserva del propio conductor.
     */
    @ExceptionHandler(ActivationTooEarlyException.class)
    public ProblemDetail handleTooEarly(ActivationTooEarlyException ex) {
        log.info("Habilitación anticipada rechazada (409): {}", ex.getMessage());
        ProblemDetail problem = withCode(
                HttpStatus.CONFLICT,
                "Todavía no podés empezar a cargar: se habilita poco antes de tu turno.",
                ActivationTooEarlyException.CODE);
        problem.setProperty("availableFrom", ex.getAvailableFrom());
        return problem;
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
