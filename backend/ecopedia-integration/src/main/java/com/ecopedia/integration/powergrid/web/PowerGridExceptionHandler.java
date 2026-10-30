package com.ecopedia.integration.powergrid.web;

import com.ecopedia.integration.powergrid.domain.PowerGridRequestRejectedException;
import com.ecopedia.integration.powergrid.domain.PowerGridUnavailableException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Los dos resultados de RedEléctrica que no son una respuesta, con un {@code code} estable.
 *
 * <p>Acotado a este paquete, como el de Pagos: un advice global le cambiaría las respuestas a los
 * demás controladores. El cuerpo es solo el código: quien llama es otro artefacto, que decide por él,
 * y el detalle técnico queda en el log de acá.
 */
@RestControllerAdvice(basePackageClasses = PowerGridExceptionHandler.class)
public class PowerGridExceptionHandler {

    @ExceptionHandler(PowerGridRequestRejectedException.class)
    public ResponseEntity<Map<String, String>> handleRejected(PowerGridRequestRejectedException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("code", exception.getReason().name()));
    }

    @ExceptionHandler(PowerGridUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleUnavailable(PowerGridUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("code", "POWER_GRID_UNAVAILABLE"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> handleInvalidBody(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST"));
    }
}
