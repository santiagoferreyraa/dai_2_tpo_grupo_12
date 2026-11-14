package com.ecopedia.core.terminal.web.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que escaneó o tipeó el conductor, tal cual: el PIN o el contenido del QR.
 *
 * <p>Va en el cuerpo y no en la URL para que el código no quede escrito en ningún log de accesos.
 */
public record ResolveAccessCodeRequest(@NotBlank @Size(max = 64) String code) {}
