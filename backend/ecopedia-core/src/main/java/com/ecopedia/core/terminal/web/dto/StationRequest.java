package com.ecopedia.core.terminal.web.dto;

import com.ecopedia.core.terminal.domain.StationData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StationRequest(
        @NotBlank(message = "El nombre es obligatorio") String name,
        @NotBlank(message = "La dirección es obligatoria") String address,
        @NotNull(message = "La latitud es obligatoria") Double latitude,
        @NotNull(message = "La longitud es obligatoria") Double longitude,
        List<String> photoUrls,
        /*
         * El código del nodo tal como lo escribe la distribuidora: mayúsculas, números y guiones
         * (CABA-CENTRO). Vacío vale, y es "sin nodo". El formato cerrado evita que viaje a la
         * distribuidora cualquier texto que alguien pegó en el formulario.
         */
        @Size(max = 40, message = "El nodo de la distribuidora tiene como máximo 40 caracteres")
                @Pattern(
                        regexp = "^$|^[A-Z0-9]+(-[A-Z0-9]+)*$",
                        message = "El nodo de la distribuidora lleva mayúsculas, números y guiones, como CABA-CENTRO")
                String nodeId) {

    /** El nodo vacío llega como "sin nodo": un texto en blanco no identifica nada. */
    public StationData toDomainData() {
        String node = nodeId == null || nodeId.isBlank() ? null : nodeId;
        return new StationData(name, address, latitude, longitude, photoUrls, node);
    }
}
