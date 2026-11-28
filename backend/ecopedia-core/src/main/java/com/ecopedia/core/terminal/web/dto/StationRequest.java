package com.ecopedia.core.terminal.web.dto;

import com.ecopedia.core.terminal.domain.StationData;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StationRequest(
        // Los largos son los de la columna: más que eso daba 500 al guardar (QA-25).
        @NotBlank(message = "El nombre es obligatorio")
                @Size(max = 120, message = "El nombre no puede superar los 120 caracteres")
                String name,
        @NotBlank(message = "La dirección es obligatoria")
                @Size(max = 255, message = "La dirección no puede superar los 255 caracteres")
                String address,
        // Coordenadas que existen: sin el rango, una estación en latitud -2E16 se guardaba igual
        // y quedaba fuera de toda búsqueda (lo encontró Schemathesis, QA-25).
        @NotNull(message = "La latitud es obligatoria")
                @DecimalMin(value = "-90", message = "La latitud va de -90 a 90")
                @DecimalMax(value = "90", message = "La latitud va de -90 a 90")
                Double latitude,
        @NotNull(message = "La longitud es obligatoria")
                @DecimalMin(value = "-180", message = "La longitud va de -180 a 180")
                @DecimalMax(value = "180", message = "La longitud va de -180 a 180")
                Double longitude,
        // Una foto en null se guardaba y la portada salía rota (lo encontró Schemathesis, QA-25).
        List<
                        @NotBlank(message = "La dirección de una foto no puede estar vacía")
                        @Size(max = 500, message = "La dirección de una foto no puede superar los 500 caracteres")
                        String>
                photoUrls,
        /*
         * El código del nodo tal como lo escribe la distribuidora: mayúsculas, números y guiones
         * (CABA-CENTRO). Vacío vale, y es "sin nodo". El formato cerrado evita que viaje a la
         * distribuidora cualquier texto que alguien pegó en el formulario.
         *
         * La regex no repite un grupo a propósito: @Pattern se evalúa aunque @Size ya haya fallado,
         * y la versión anterior, ^$|^[A-Z0-9]+(-[A-Z0-9]+)*$, desbordaba la pila con unos 20 KB de
         * "A-A-A-..." y la respuesta era 500 (SonarCloud S5998; InputLimitsTest#hugeGridNode).
         * Esta acepta y rechaza lo mismo: ni empieza ni termina con guion, ni lleva dos seguidos.
         * No usa cuantificadores posesivos porque el OpenAPI publica esta regex y Schemathesis
         * (QA-25) no los entiende.
         */
        @Size(max = 40, message = "El nodo de la distribuidora tiene como máximo 40 caracteres")
                @Pattern(
                        regexp = "^(?!-)(?!.*--)(?!.*-$)[A-Z0-9-]*$",
                        message = "El nodo de la distribuidora lleva mayúsculas, números y guiones, como CABA-CENTRO")
                String nodeId) {

    /** El nodo vacío llega como "sin nodo": un texto en blanco no identifica nada. */
    public StationData toDomainData() {
        String node = nodeId == null || nodeId.isBlank() ? null : nodeId;
        return new StationData(name, address, latitude, longitude, photoUrls, node);
    }
}
