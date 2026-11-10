package com.ecopedia.core.user.web.dto;

import com.ecopedia.core.user.domain.RegistrationData;
import com.ecopedia.core.user.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos del registro público de un usuario (RF01).
 *
 * <p><b>El rol no se pide, y es la razón de ser de este comentario.</b> El registro está en
 * {@code permitAll}, así que aceptar un rol por parámetro deja que cualquiera se emita a sí
 * mismo una cuenta {@code ADMIN} y entre al backoffice por la puerta grande: los
 * {@code @PreAuthorize} quedan intactos y no protegen nada, porque el atacante llega con el rol
 * que hace falta. Todo el que se registra nace {@code CONDUCTOR}; elevar a {@code CPO} o
 * {@code ADMIN} es una operación administrativa, no una casilla del formulario de alta.
 *
 * <p>Un cuerpo que igual mande {@code "role"} no falla: Jackson descarta las propiedades que el
 * record no declara. Se ignora en silencio y el usuario nace conductor igual.
 */
public record RegisterRequest(
        @NotBlank(message = "El email es obligatorio") @Email(message = "Formato de email inválido") String email,
        @NotBlank(message = "La contraseña es obligatoria")
                @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
                String password,
        @NotBlank(message = "El nombre completo es obligatorio") String fullName) {
    public RegistrationData toDomainData() {
        return new RegistrationData(email, password, fullName, Role.CONDUCTOR);
    }
}
