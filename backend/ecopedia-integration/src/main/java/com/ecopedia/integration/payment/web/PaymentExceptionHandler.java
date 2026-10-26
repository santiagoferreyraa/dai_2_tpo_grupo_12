package com.ecopedia.integration.payment.web;

import com.ecopedia.integration.payment.domain.PaymentGatewayUnavailableException;
import java.util.stream.Collectors;
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
 * <p><b>400 y no 401 ni 403</b>, incluso cuando la tarjeta es de otro. El cliente HTTP del frontend
 * cierra la sesión ante cualquier 401 o 403, porque ahí no puede distinguir un token vencido de un
 * permiso que falta. Contestar 403 al intento de borrar una tarjeta ajena desconectaría al usuario
 * en vez de mostrarle el error — y de paso le confirmaría que ese id existe, que es justo lo que el
 * servicio evita contestando "no se encontró".
 *
 * <p><b>El alcance está acotado a este paquete</b> por {@code basePackageClasses}: un
 * {@code @RestControllerAdvice} global se aplicaría también a los controladores del cliente SOAP que
 * van a vivir en este mismo artefacto, y les impondría una traducción de errores que no es la suya.
 */
@RestControllerAdvice(basePackageClasses = PaymentExceptionHandler.class)
public class PaymentExceptionHandler {

    /** Tarjeta rechazada por la pasarela, duplicada, ajena, inexistente o tope alcanzado. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidRequest(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /**
     * La pasarela está caída, no contesta a tiempo o rechazó nuestras credenciales.
     *
     * <p>503 y no 500: el pedido estaba bien y el sistema también, lo que falta es un tercero. El
     * mensaje le dice al conductor que pruebe más tarde, que es lo único que puede hacer.
     */
    @ExceptionHandler(PaymentGatewayUnavailableException.class)
    public ProblemDetail handleGatewayUnavailable(PaymentGatewayUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
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
