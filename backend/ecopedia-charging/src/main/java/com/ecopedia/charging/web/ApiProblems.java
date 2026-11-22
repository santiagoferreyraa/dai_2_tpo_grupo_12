package com.ecopedia.charging.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Los cuerpos de error (RFC 9457) que tienen que salir iguales vengan de donde vengan.
 *
 * <p><b>Un solo 404 para todo.</b> Una ruta que no existe, un recurso que no existe y una operación
 * de otro rol (SEC-05) responden exactamente el mismo cuerpo. Si el texto cambiara según el caso,
 * quien prueba podría separar "no tenés permiso" de "eso no está", o saber qué ids existen: el
 * motivo real va al log, nunca a la respuesta.
 *
 * <p><b>Por qué hace falta escribirlo a mano.</b> El 401 y el 404 del rol equivocado los contestan
 * los filtros de Spring Security, antes de que el pedido llegue a Spring MVC y a los
 * {@code @RestControllerAdvice}. Para que salgan idénticos a los de MVC se arman con los mismos
 * métodos y se escriben con el mismo {@link ObjectMapper}.
 */
public final class ApiProblems {

    /** El único texto de un 404, sea cual sea el motivo. */
    public static final String NOT_FOUND_DETAIL = "No se encontró el recurso pedido.";

    /** Un 400 sin un motivo que valga la pena mostrar: parámetro mal escrito, cuerpo ilegible. */
    public static final String INVALID_REQUEST_DETAIL = "La solicitud no es válida.";

    /** Sin token, o con uno vencido, alterado o firmado con otra clave. */
    public static final String UNAUTHORIZED_DETAIL = "Hace falta una sesión válida.";

    /** Lo que ve el cliente de un error inesperado; el detalle queda en el log. */
    public static final String UNEXPECTED_DETAIL = "Ocurrió un error inesperado. Probá de nuevo en unos minutos.";

    /**
     * Una transición de la reserva que su estado no admite (409 {@code BOOKING_STATE_CONFLICT}, QA-26):
     * el mismo texto desde Reservas y desde SesionesDeCarga.
     */
    public static final String STATE_CONFLICT_DETAIL = "La reserva no admite ese cambio en su estado actual.";

    /** El cargador contestó que no a la orden de encendido. */
    public static final String CHARGER_REJECTED_DETAIL =
            "El cargador no aceptó la orden de encendido. Probá con otro conector o avisale al operador.";

    /** El cargador no contestó, o no se lo pudo alcanzar. */
    public static final String CHARGER_UNAVAILABLE_DETAIL = "El cargador no respondió. Probá de nuevo en unos minutos.";

    private ApiProblems() {}

    public static ProblemDetail notFound() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, NOT_FOUND_DETAIL);
    }

    public static ProblemDetail invalidRequest() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_REQUEST_DETAIL);
    }

    public static ProblemDetail unauthorized() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, UNAUTHORIZED_DETAIL);
    }

    public static ProblemDetail unexpected() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_DETAIL);
    }

    /**
     * Escribe el problema desde un filtro, como lo haría Spring MVC: mismo tipo de contenido y
     * {@code instance} con la ruta pedida.
     */
    public static void write(
            HttpServletRequest request, HttpServletResponse response, ProblemDetail problem, ObjectMapper mapper)
            throws IOException {
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
