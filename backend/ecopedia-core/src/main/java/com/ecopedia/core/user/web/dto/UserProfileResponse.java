package com.ecopedia.core.user.web.dto;

import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.vehicle.domain.VehicleModel;
import com.ecopedia.core.vehicle.web.dto.VehicleModelResponse;
import java.time.Instant;

/**
 * El perfil tal como viaja en el JSON.
 *
 * <p><b>El auto va embebido y no como un id suelto.</b> Con solo el id, toda pantalla que muestre
 * el perfil tendría que pedir después el catálogo entero para traducirlo a una ficha —y la
 * portada necesita la ficha apenas abre, para contar estaciones compatibles y estimar cuánto
 * tarda una carga—. La resuelve {@code UserProfileAssembler} con {@code VehicleService}: una
 * consulta por perfil, y una sola para toda la lista del {@code ADMIN}.
 *
 * <p>{@code vehicle} en {@code null} es "todavía no eligió auto", que es un estado normal y no un
 * error. Ver {@code User.vehicleModelId}.
 */
public record UserProfileResponse(
        Long id,
        String email,
        String fullName,
        Role role,
        boolean active,
        Instant createdAt,
        VehicleModelResponse vehicle,
        // La versión del ETag (QA-30): el front la manda en If-Match al guardar el perfil.
        long version) {

    /**
     * @param vehicle la ficha del auto del usuario, ya resuelta por {@code VehicleService}, o
     *     {@code null} si no tiene: Usuarios guarda solo el id (RNF09). Ver {@code UserProfileAssembler}.
     */
    public static UserProfileResponse fromDomain(User user, VehicleModel vehicle) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                vehicle == null ? null : VehicleModelResponse.fromDomain(vehicle),
                user.getVersion());
    }
}
