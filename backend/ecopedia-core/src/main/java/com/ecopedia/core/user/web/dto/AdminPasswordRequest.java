package com.ecopedia.core.user.web.dto;

/**
 * La contraseña con que el {@code ADMIN} confirma una baja. Ver {@code AdminConfirmation}.
 */
public record AdminPasswordRequest(String password) {}
