package com.ecopedia.core.user.domain;

/**
 * La contraseña con que el {@code ADMIN} confirmó una baja o un cambio de rol no es la suya (o no
 * vino). Cuenta como un intento fallido de login: ver {@code LoginAttempts}.
 */
public class AdminPasswordRejectedException extends RuntimeException {

    /** El {@code code} estable de la respuesta, para que el front elija sus palabras. */
    public static final String CODE = "PASSWORD_INCORRECT";

    public AdminPasswordRejectedException() {
        super("La contraseña de confirmación no es correcta");
    }
}
