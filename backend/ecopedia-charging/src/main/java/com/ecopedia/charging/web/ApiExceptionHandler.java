package com.ecopedia.charging.web;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * La red de abajo: todo error que no traduzca un manejador del paquete sale en Problem Details
 * (RFC 9457, QA-31), con el estado correcto y sin mensajes internos de Java.
 *
 * <p><b>Va último a propósito</b> ({@code LOWEST_PRECEDENCE}). Los manejadores acotados a un
 * paquete van primero y deciden lo suyo; este solo recibe lo que
 * ellos no atienden. Spring elige el primer {@code @ControllerAdvice} que sepa manejar la
 * excepción, no el más específico, así que el orden tiene que estar escrito.
 *
 * <p>Lo que traduce:
 *
 * <ul>
 *   <li>Los errores de Spring MVC (los hereda de {@link ResponseEntityExceptionHandler}): un
 *       parámetro con formato inválido (DEF-12) o un cuerpo ilegible, 400; un método no
 *       permitido, 405; un tipo de contenido no aceptado, 415; una ruta inexistente, el 404 de
 *       siempre. El texto es propio: el de Spring nombra clases y conversiones.
 *   <li>{@link IllegalArgumentException}: 400 con el mensaje, que en los servicios está escrito
 *       para quien llama.
 *   <li>Cualquier otra cosa: 500 con un texto genérico; la excepción, al log.
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidRequest(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /**
     * Los rechazos de seguridad no son de este manejador: se devuelven a Spring Security, que
     * contesta 401 sin sesión y 404 con el rol equivocado. Sin esto, el {@code Exception} de abajo
     * los convertiría en 500.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void handleSecurity(RuntimeException exception) {
        throw exception;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Error inesperado", exception);
        return ApiProblems.unexpected();
    }

    /** Pone el texto propio sobre las respuestas de los errores de Spring MVC. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return super.handleExceptionInternal(exception, problemFor(exception, status), headers, status, request);
    }

    private static ProblemDetail problemFor(Exception exception, HttpStatusCode status) {
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ApiProblems.notFound();
        }
        if (status.is5xxServerError()) {
            log.error("Error de Spring MVC", exception);
            return ProblemDetail.forStatusAndDetail(status, ApiProblems.UNEXPECTED_DETAIL);
        }
        return ProblemDetail.forStatusAndDetail(status, detailFor(exception, status));
    }

    private static String detailFor(Exception exception, HttpStatusCode status) {
        Stream<? extends MessageSourceResolvable> errors = Stream.empty();
        if (exception instanceof MethodArgumentNotValidException invalid) {
            errors = invalid.getBindingResult().getAllErrors().stream();
        } else if (exception instanceof HandlerMethodValidationException invalid) {
            errors = invalid.getAllErrors().stream();
        }
        // Todos los mensajes juntos: con uno solo, corregir el formulario es un ida y vuelta por error.
        String messages = errors.map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .filter(message -> !message.isBlank())
                .distinct()
                .collect(Collectors.joining(". "));
        if (!messages.isBlank()) {
            return messages;
        }
        return switch (status.value()) {
            case 405 -> "La operación no está permitida en esta ruta.";
            case 406 -> "No se puede responder en el formato pedido.";
            case 415 -> "El tipo de contenido no está soportado.";
            default -> ApiProblems.INVALID_REQUEST_DETAIL;
        };
    }
}
