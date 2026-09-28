package com.ecopedia.async.security;

import org.springframework.security.core.Authentication;

/**
 * Saca del token quién está haciendo la petición: el id del usuario, que Usuarios pone en el
 * {@code subject}.
 *
 * <p>Es el mismo id con el que Reservas publica cada aviso como destinatario, así que "mis avisos"
 * se resuelve comparando dos números, sin preguntarle nada a core.
 */
public final class AuthenticatedUser {

    private AuthenticatedUser() {}

    /**
     * El id del usuario autenticado.
     *
     * <p>Solo se llama desde métodos que {@code @PreAuthorize} ya obligó a estar autenticados:
     * llegar acá sin {@link Authentication} es una anotación que se borró, y falla fuerte.
     */
    public static Long idOf(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("Se pidió el usuario autenticado en un endpoint sin autenticación");
        }
        return Long.valueOf(authentication.getName());
    }
}
