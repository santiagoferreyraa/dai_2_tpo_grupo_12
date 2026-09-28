package com.ecopedia.async.notification.web;

import com.ecopedia.async.notification.domain.NotificationAccessDeniedException;
import com.ecopedia.async.notification.domain.NotificationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce los errores de Notificaciones a respuestas HTTP.
 *
 * <p>Acotado a este paquete con {@code basePackageClasses}, igual que en los otros módulos: sin
 * acotar, atraparía también las excepciones de cualquier controlador que se sume al artefacto.
 */
@RestControllerAdvice(basePackageClasses = NotificationExceptionHandler.class)
public class NotificationExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    public ProblemDetail handleNotFound(NotificationNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(NotificationAccessDeniedException.class)
    public ProblemDetail handleSomeoneElses(NotificationAccessDeniedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }
}
