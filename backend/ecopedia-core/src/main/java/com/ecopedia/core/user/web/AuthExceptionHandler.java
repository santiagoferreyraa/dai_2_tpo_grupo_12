package com.ecopedia.core.user.web;

import com.ecopedia.core.vehicle.domain.VehicleModelNotFoundException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce a respuestas con mensaje los errores de los endpoints de usuarios.
 *
 * <p><b>Sin esta clase el login roto era un 500.</b> {@code UserServiceImpl} señala las
 * credenciales inválidas y el email repetido con {@code IllegalArgumentException}, y sin nadie
 * que la interprete Spring la trata como una falla del servidor: el usuario que se equivoca de
 * contraseña recibe un "Error 500" y el motivo real queda en el log. No es solo cosmético —un
 * 500 dice "el servidor se rompió", cuando lo que pasó es que el dato que llegó no sirve—.
 *
 * <p><b>Por qué 400 y no 401.</b> El cliente HTTP del frontend cierra la sesión ante cualquier
 * 401 o 403, porque ahí no puede distinguir un token vencido de un permiso que falta. Contestar
 * 401 a un login fallido dispararía el cartel de "tu sesión venció" justo cuando no hay ninguna
 * sesión que vencer. Lo que ocurrió es que el cuerpo del pedido no es válido, y eso es un 400.
 *
 * <p><b>Los errores de validación también perdían su mensaje.</b> Las anotaciones de los DTO
 * ({@code @Email}, {@code @Size}) traen texto escrito para el usuario, pero la respuesta por
 * defecto de Spring Boot no lo incluye: llega un 400 con {@code timestamp}, {@code status},
 * {@code error} y {@code path}, y nada más. Se arman acá en un único mensaje.
 *
 * <p>La respuesta es un {@link ProblemDetail} (RFC 7807) porque es el formato que el cliente
 * del frontend lee primero, por su campo {@code detail}.
 */
@RestControllerAdvice(basePackageClasses = AuthExceptionHandler.class)
public class AuthExceptionHandler {

    /** Credenciales inválidas, usuario dado de baja, email ya registrado, id inexistente. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidRequest(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /**
     * El auto elegido no está en el catálogo.
     *
     * <p><b>400 y no 404, aunque la excepción diga "not found".</b> Lo que no se encontró no es
     * el recurso que se pidió —el perfil existe y es el de quien está logueado—, sino un valor
     * de adentro del cuerpo. Un 404 acá le diría a la pantalla que se equivocó de dirección,
     * cuando lo que pasó es que mandó un modelo que no se ofrece.
     */
    @ExceptionHandler(VehicleModelNotFoundException.class)
    public ProblemDetail handleUnknownVehicleModel(VehicleModelNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /** Campos que no pasan las anotaciones de validación del DTO. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidFields(MethodArgumentNotValidException exception) {
        // Se juntan todos los mensajes: con un solo campo devuelto, corregir el formulario es
        // un ida y vuelta por error en vez de uno solo.
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(message -> message != null && !message.isBlank())
                .distinct()
                .collect(Collectors.joining(". "));

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, detail.isBlank() ? "Los datos enviados no son válidos" : detail);
    }
}
