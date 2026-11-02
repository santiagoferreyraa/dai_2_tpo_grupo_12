package com.ecopedia.core.user.web.dto;

import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.vehicle.web.dto.VehicleModelResponse;
import java.time.Instant;

/**
 * El perfil tal como viaja en el JSON.
 *
 * <p><b>El auto va embebido y no como un id suelto.</b> Con solo el id, toda pantalla que muestre
 * el perfil tendría que pedir después el catálogo entero para traducirlo a una ficha —y la
 * portada necesita la ficha apenas abre, para contar estaciones compatibles y estimar cuánto
 * tarda una carga—. La fila ya está cargada cuando se arma esta respuesta; mandarla no cuesta una
 * consulta más.
 *
 * <p>{@code vehicle} en {@code null} es "todavía no eligió auto", que es un estado normal y no un
 * error. Ver {@code User.vehicleModel}.
 */
public record UserProfileResponse(
        Long id,
        String email,
        String fullName,
        Role role,
        boolean active,
        Instant createdAt,
        VehicleModelResponse vehicle) {

    public static UserProfileResponse fromDomain(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getVehicleModel() == null ? null : VehicleModelResponse.fromDomain(user.getVehicleModel()));
    }
}
