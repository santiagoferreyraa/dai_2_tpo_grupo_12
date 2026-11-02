package com.ecopedia.integration.powergrid.web;

import com.ecopedia.integration.powergrid.domain.PowerGridRequestRejectedException;
import com.ecopedia.integration.powergrid.domain.PowerGridUnavailableException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PowerGridExceptionHandler {

    @ExceptionHandler(PowerGridRequestRejectedException.class)
    public ProblemDetail handleRejected(PowerGridRequestRejectedException exception) {
        return code(HttpStatus.UNPROCESSABLE_ENTITY, exception.getReason().name());
    }

    @ExceptionHandler(PowerGridUnavailableException.class)
    public ProblemDetail handleUnavailable(PowerGridUnavailableException exception) {
        return code(HttpStatus.SERVICE_UNAVAILABLE, "POWER_GRID_UNAVAILABLE");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ProblemDetail handleInvalidBody(Exception exception) {
        return code(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    /** Problem Details con el {@code code} estable que lee charging (QA-31). */
    private static ProblemDetail code(HttpStatus status, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setProperty("code", code);
        return problem;
    }
}
