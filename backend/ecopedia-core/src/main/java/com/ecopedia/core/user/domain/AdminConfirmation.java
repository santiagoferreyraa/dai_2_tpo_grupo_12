package com.ecopedia.core.user.domain;

/**
 * Quién pide una baja o un cambio de rol, y la contraseña con que lo confirma.
 *
 * <p>El mail sale del token, nunca del cuerpo del pedido: es el {@code ADMIN} que está logueado. La
 * contraseña es la suya, escrita en el momento (decidido por Manuel el 02/11). Con un token robado
 * o una sesión abierta en una máquina ajena se llega hasta acá; sin la contraseña, no se pasa.
 *
 * @param adminEmail el mail del {@code ADMIN} que actúa.
 * @param password su contraseña, o {@code null} si el pedido no la trajo: cuenta como equivocada.
 */
public record AdminConfirmation(String adminEmail, String password) {}
