package com.ecopedia.charging.security;

import com.ecopedia.charging.web.ApiProblems;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * <p><b>Solo con sesión.</b> El pedido sin sesión válida no pasa por acá, sino por
 * {@link UnauthorizedEntryPoint}, que contesta 401.
 *
 * <p><b>El cuerpo es el de cualquier 404, byte por byte</b>: lo arma {@link ApiProblems}, el mismo
 * que usa {@code ApiExceptionHandler} para una ruta inexistente.
 *
 * <p><b>El log lleva el id del usuario, no el email</b> (DEF-03). Antes iba el nombre de la
 * autenticación, que acá es el {@link AuthenticatedUser} entero, email incluido.
 */
@Component
public class NotFoundAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(NotFoundAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public NotFoundAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object userId = authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? user.id()
                : null;
        log.warn(
                "Acceso denegado (403, respondido como 404): usuario {} con {} en {} {}",
                userId == null ? "?" : userId,
                authentication == null ? "[]" : authentication.getAuthorities(),
                request.getMethod(),
                request.getRequestURI());
        ApiProblems.write(request, response, ApiProblems.notFound(), objectMapper);
    }
}
