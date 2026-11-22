package com.ecopedia.core.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El cambio de contraseña del propio usuario.
 *
 * <p><b>Las dos contraseñas viajan juntas y la actual no es un trámite.</b> Quien llega hasta
 * este endpoint ya tiene un token válido, así que sin ese campo alcanzaría con una sesión
 * abierta en una máquina ajena —o con un token robado— para cambiar la clave y quedarse con la
 * cuenta. Pedir la vigente es lo único que separa al dueño de quien pasaba por ahí.
 *
 * <p>El mínimo de la nueva es el mismo que el del registro. Si fuera más flojo acá, el camino
 * para tener una contraseña corta sería registrarse con una larga y cambiarla después.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Ingresá tu contraseña actual") String currentPassword,
        @NotBlank(message = "Elegí una contraseña nueva")
                @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
                String newPassword) {}
