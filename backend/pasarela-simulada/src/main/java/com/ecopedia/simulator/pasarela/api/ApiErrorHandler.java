package com.ecopedia.simulator.pasarela.api;

import com.ecopedia.simulator.pasarela.domain.GatewayException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Todos los errores de la pasarela salen con la misma forma:
 *
 * <pre>{"error": {"type": "card_error", "code": "expired_card", "message": "The card has expired"}}</pre>
 *
 * <p>No es el {@code ProblemDetail} que usa Ecopedia, y está bien que no lo sea: es el formato de
 * otro sistema. Que Ecopedia lo lea y lo traduzca es parte del trabajo del Adapter.
 */
@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ErrorEnvelope> handleGateway(GatewayException exception) {
        return respond(exception.status(), exception.type(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleInvalidFields(MethodArgumentNotValidException exception) {
        String fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField())
                .distinct()
                .collect(Collectors.joining(", "));
        return respond(
                HttpStatus.BAD_REQUEST, "invalid_request_error", "parameter_invalid", "Invalid parameters: " + fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleUnreadable(HttpMessageNotReadableException exception) {
        return respond(
                HttpStatus.BAD_REQUEST, "invalid_request_error", "invalid_json", "The request body is not valid JSON");
    }

    static ResponseEntity<ErrorEnvelope> respond(HttpStatus status, String type, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorEnvelope(new ErrorBody(type, code, message)));
    }

    public record ErrorEnvelope(ErrorBody error) {}

    public record ErrorBody(String type, String code, String message) {}
}
