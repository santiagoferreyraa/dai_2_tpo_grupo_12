package com.ecopedia.charging.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Lo que recibe un usuario con sesión que llama a una operación de otro rol: un 404, igual al de
 * una ruta que no existe (SEC-05). El 403 queda solo en el log.
 *
 * <p><b>Por qué ocultarlo.</b> Un 403 le confirma a quien prueba que la operación existe y que
 * solo le falta el rol: es el mapa de lo que conviene atacar. Con un 404 no puede separar "no
 * tenés permiso" de "eso no está". El que tiene el rol correcto no nota nada.
 *
 * <p><b>Solo con sesión.</b> El pedido anónimo —sin token, o con uno vencido o alterado— no pasa
 * por acá, sino por el punto de entrada de Spring, y sigue contestando 403: el front lo usa para
 * darse cuenta de que la sesión venció. Con un 404 no tendría cómo.
 *
 * <p><b>El cuerpo es el de cualquier 404</b> porque no se arma acá: {@code sendError} manda el
 * pedido al mismo {@code /error} que contesta una ruta inexistente.
 */
@Component
public class NotFoundAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(NotFoundAccessDeniedHandler.class);

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.warn(
                "Acceso denegado (403, respondido como 404): usuario {} con {} en {} {}",
                authentication == null ? "?" : authentication.getName(),
                authentication == null ? "[]" : authentication.getAuthorities(),
                request.getMethod(),
                request.getRequestURI());
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
