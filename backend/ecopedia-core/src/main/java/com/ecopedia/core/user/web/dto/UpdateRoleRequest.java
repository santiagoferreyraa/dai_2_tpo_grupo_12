package com.ecopedia.core.user.web.dto;

import com.ecopedia.core.user.domain.Role;
import jakarta.validation.constraints.NotNull;

/**
 * El rol nuevo y la contraseña del {@code ADMIN} que lo confirma.
 *
 * <p>La contraseña no lleva {@code @NotBlank}: sin ella, el servicio responde "contraseña incorrecta".
 * Ver el comentario de {@code UserController#deactivateUser}.
 */
public record UpdateRoleRequest(@NotNull(message = "El rol es obligatorio") Role role, String password) {}
