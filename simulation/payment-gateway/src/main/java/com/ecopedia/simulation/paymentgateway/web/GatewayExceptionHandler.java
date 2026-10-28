package com.ecopedia.simulation.paymentgateway.web;

import com.ecopedia.simulation.paymentgateway.domain.GatewayException;
import com.ecopedia.simulation.paymentgateway.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Todos los errores de la API con la misma forma: estado, {@code code} estable y mensaje. */
@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ErrorResponse> handleGateway(GatewayException exception) {
        HttpStatus status = exception.getStatus();
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), errorName(status), exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException exception) {
        String code = PaymentController.IDEMPOTENCY_HEADER.equalsIgnoreCase(exception.getHeaderName())
                ? "missing_idempotency_key"
                : "missing_header";
        return badRequest(code, "Missing header " + exception.getHeaderName());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleInvalidBody(Exception exception) {
        return badRequest("invalid_request", "Request body is missing or invalid");
    }

    private static ResponseEntity<ErrorResponse> badRequest(String code, String message) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "bad_request", code, message));
    }

    private static String errorName(HttpStatus status) {
        return status.getReasonPhrase().toLowerCase().replace(' ', '_');
    }
}
