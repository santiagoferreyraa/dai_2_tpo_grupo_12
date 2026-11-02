package com.ecopedia.core.user.domain;

/**
 * Se pidió dar de baja o cambiarle el rol a una cuenta {@code ADMIN}, que desde la aplicación no se
 * toca (decidido por Manuel el 02/11): se hace solo por la base. La regla incluye la propia cuenta,
 * así que de paso nadie se saca el rol a sí mismo ni deja la plataforma sin administradores.
 */
public class AdminAccountProtectedException extends RuntimeException {

    /** El {@code code} estable de la respuesta, para que el front elija sus palabras. */
    public static final String CODE = "ADMIN_PROTECTED";

    public AdminAccountProtectedException() {
        super("Una cuenta ADMIN no se da de baja ni cambia de rol desde la aplicación");
    }
}
