package com.ecopedia.core.user.domain;

/**
 * Al cambiar la contraseña, la actual que escribió el usuario no es la suya.
 *
 * <p>Lleva un {@code code} estable porque el front elige sus propias palabras (directiva 6) y no
 * muestra el {@code detail}. Extiende {@link IllegalArgumentException} para seguir siendo, para todo
 * lo demás, un pedido inválido (400): no 401, que le cerraría la sesión a alguien que sí la tiene.
 */
public class CurrentPasswordIncorrectException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /** El {@code code} estable de la respuesta. */
    public static final String CODE = "CURRENT_PASSWORD_INCORRECT";

    public CurrentPasswordIncorrectException() {
        super("La contraseña actual no es correcta");
    }
}
