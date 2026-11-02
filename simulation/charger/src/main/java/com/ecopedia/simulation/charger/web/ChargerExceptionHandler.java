package com.ecopedia.simulation.charger.web;

import com.ecopedia.simulation.charger.domain.ChargerException;
import com.ecopedia.simulation.charger.web.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Todos los errores de la API con la misma forma: estado, {@code code} estable y mensaje. */
@RestControllerAdvice
public class ChargerExceptionHandler {

    @ExceptionHandler(ChargerException.class)
    public ResponseEntity<ErrorResponse> handleCharger(ChargerException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(exception.getStatus().value(), exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleInvalidBody(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "invalid_request", "Request body is missing or invalid"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPath(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "invalid_connector", "Connector id must be a number"));
    }
}
