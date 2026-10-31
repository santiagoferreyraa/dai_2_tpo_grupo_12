package com.ecopedia.charging.booking.web;

import com.ecopedia.charging.booking.domain.BookingNotFoundException;
import com.ecopedia.charging.booking.domain.ConnectorCatalogUnavailableException;
import com.ecopedia.charging.booking.domain.ConnectorNotBookableException;
import com.ecopedia.charging.booking.domain.ConnectorNotFoundException;
import com.ecopedia.charging.booking.domain.DriverAlreadyBookedException;
import com.ecopedia.charging.booking.domain.HoldExpiredException;
import com.ecopedia.charging.booking.domain.HoldNotFoundException;
import com.ecopedia.charging.booking.domain.InvalidBookingRequestException;
import com.ecopedia.charging.booking.domain.SlotUnavailableException;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de Reservas a respuestas HTTP con cuerpo {@code ProblemDetail}, el
 * mismo formato que usa core: el cliente HTTP del front ya sabe leer su {@code detail}.
 *
 * <p>Acotado con {@code basePackageClasses} al paquete web de Reservas, por lo mismo que en
 * core: un advice suelto atraparía también las excepciones de SesionesDeCarga cuando exista.
 *
 * <p>Cada caso tiene su código, y no es por prolijidad: el front decide qué decirle al conductor
 * según el número. 404 es "eso no existe" —un conector, una reserva o una retención; también la
 * de otro conductor, que desde afuera no se distingue (SEC-06)—, 409 es "ese slot no se puede",
 * 410 es "se te venció el tiempo para confirmar" y 503 es "probá de nuevo en un rato".
 */
@RestControllerAdvice(basePackageClasses = BookingExceptionHandler.class)
public class BookingExceptionHandler {

    @ExceptionHandler(InvalidBookingRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidBookingRequestException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({ConnectorNotFoundException.class, HoldNotFoundException.class, BookingNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    /**
     * La retención venció: 410 y no 404.
     *
     * <p>El id existió y dejó de valer por el paso del tiempo, que es exactamente lo que significa
     * GONE. Al front le importa la diferencia: con un 410 le ofrece al conductor volver a tomar
     * el mismo horario, que casi siempre sigue libre.
     */
    @ExceptionHandler(HoldExpiredException.class)
    public ProblemDetail handleExpiredHold(HoldExpiredException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, exception.getMessage());
    }

    @ExceptionHandler({ConnectorNotBookableException.class, SlotUnavailableException.class})
    public ProblemDetail handleConflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    /**
     * El conductor ya tiene comprometido ese tiempo: 409 igual que el slot tomado, pero con
     * {@code code}.
     *
     * <p>El código es necesario y no decorativo: los dos conflictos llegan al front como 409 y le
     * tiene que decir cosas distintas al conductor. "Ese horario ya no está disponible, probá con
     * otro" sobre una reserva propia que se cruza manda a buscar un conector que no es el problema.
     */
    @ExceptionHandler(DriverAlreadyBookedException.class)
    public ProblemDetail handleDriverAlreadyBooked(DriverAlreadyBookedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setProperty("code", exception.code());
        return problem;
    }

    /**
     * La reserva cambió entre que se leyó y se guardó: el barrido de la incomparecencia la cerró
     * mientras el conductor la cancelaba. 409 con {@code code}, para que el front recargue la lista
     * en vez de decir que se canceló.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleChangedMeanwhile(OptimisticLockingFailureException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "La reserva cambió mientras se la modificaba: " + exception.getMessage());
        problem.setProperty("code", "BOOKING_CHANGED");
        return problem;
    }

    @ExceptionHandler(ConnectorCatalogUnavailableException.class)
    public ProblemDetail handleCoreDown(ConnectorCatalogUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "No se pudo verificar el conector. Probá de nuevo en un momento.");
    }

    /** Campos que no pasan las anotaciones del DTO. Se juntan todos, igual que en core. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidFields(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(message -> message != null && !message.isBlank())
                .distinct()
                .collect(Collectors.joining(". "));

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, detail.isBlank() ? "Los datos enviados no son válidos" : detail);
    }
}
