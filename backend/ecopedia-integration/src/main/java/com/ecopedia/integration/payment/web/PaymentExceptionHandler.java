package com.ecopedia.integration.payment.web;

import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import com.ecopedia.integration.payment.domain.PaymentMethodNotFoundException;
import com.ecopedia.integration.web.ApiProblems;
import java.util.stream.Collectors;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce a respuestas con mensaje los errores de los endpoints de Pagos.
 *
 * <p>Mismo papel que {@code AuthExceptionHandler} en core, y por el mismo motivo: el negocio señala
 * con {@link IllegalArgumentException} todo lo que es "el dato que llegó no sirve" —tarjeta vencida,
 * número mal copiado, marca no aceptada, tarjeta que no es tuya—, y sin nadie que la interprete
 * Spring la trata como una falla del servidor. El conductor que se equivoca un dígito recibiría un
 * 500 y el motivo real quedaría en el log.
 *
 * <p><b>La tarjeta inexistente, dada de baja o ajena da 404</b>, el mismo de cualquier ruta que no
 * existe (DEF-07). No 401 ni 403: el cliente HTTP del frontend cierra la sesión ante cualquiera de
 * los dos, y un 403 le confirmaría a quien prueba que ese id es una tarjeta real (SEC-06).
 *
 * <p><b>El alcance está acotado a este paquete</b> por {@code basePackageClasses}, y va antes que
 * {@code ApiExceptionHandler}, que recibe lo que este no atiende.
 */
@RestControllerAdvice(basePackageClasses = PaymentExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentMethodNotFoundException.class)
    public ProblemDetail handleNotFound(PaymentMethodNotFoundException exception) {
        return ApiProblems.notFound();
    }

    /**
     * Un parámetro con formato inválido, como un id que no es número (DEF-12). Tiene que estar acá:
     * Spring busca también en la causa, que es una {@code NumberFormatException}, y sin esto la
     * atraparía el manejador de {@code IllegalArgumentException} de abajo, con el mensaje de Java.
     */
    @ExceptionHandler(TypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(TypeMismatchException exception) {
        return ApiProblems.invalidRequest();
    }

    /** Tarjeta rechazada por la pasarela, duplicada o tope alcanzado. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidRequest(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /**
     * La pasarela no contestó: 503, con un {@code code} estable para que el front no tenga que
     * leer el texto, y un mensaje que le dice al conductor que pruebe en un rato.
     */
    @ExceptionHandler(PaymentGatewayUnavailableException.class)
    public ProblemDetail handleGatewayUnavailable(PaymentGatewayUnavailableException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problem.setProperty("code", "PAYMENT_GATEWAY_UNAVAILABLE");
        return problem;
    }

    /** Campos que no pasan las anotaciones de validación del DTO. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidFields(MethodArgumentNotValidException exception) {
        // Se juntan todos los mensajes: con un solo campo devuelto, corregir el formulario es un
        // ida y vuelta por error en vez de uno solo.
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(message -> message != null && !message.isBlank())
                .distinct()
                .collect(Collectors.joining(". "));

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, detail.isBlank() ? "Los datos enviados no son válidos" : detail);
    }
}
