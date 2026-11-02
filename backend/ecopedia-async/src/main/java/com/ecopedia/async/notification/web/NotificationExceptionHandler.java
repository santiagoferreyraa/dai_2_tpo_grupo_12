package com.ecopedia.async.notification.web;

import com.ecopedia.async.notification.domain.NotificationNotFoundException;
import com.ecopedia.async.web.ApiProblems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NotificationExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    public ProblemDetail handleNotFound(NotificationNotFoundException exception) {
        // El mismo 404 que una ruta inexistente: el id y el motivo no salen en la respuesta.
        return ApiProblems.notFound();
    }
}
