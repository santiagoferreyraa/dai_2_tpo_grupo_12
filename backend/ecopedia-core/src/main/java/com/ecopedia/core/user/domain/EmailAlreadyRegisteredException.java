package com.ecopedia.core.user.domain;

/**
 * El registro pide un email que ya tiene una cuenta.
 *
 * <p>Lleva un {@code code} estable porque el front elige sus propias palabras (directiva 6) y no
 * muestra el {@code detail}. Extiende {@link IllegalArgumentException} para seguir siendo, para todo
 * lo demás, un pedido inválido (400).
 */
public class EmailAlreadyRegisteredException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /** El {@code code} estable de la respuesta. */
    public static final String CODE = "EMAIL_ALREADY_REGISTERED";

    public EmailAlreadyRegisteredException(String email) {
        super("El email ya está registrado: " + email);
    }
}
