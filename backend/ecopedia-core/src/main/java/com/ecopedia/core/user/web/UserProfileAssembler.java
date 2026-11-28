package com.ecopedia.core.user.web;

import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.web.dto.UserProfileResponse;
import com.ecopedia.core.vehicle.domain.VehicleModel;
import com.ecopedia.core.vehicle.domain.VehicleService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Arma el perfil que viaja en el JSON, con la ficha del auto resuelta.
 *
 * <p><b>Existe porque Usuarios guarda solo el id del auto</b> (RNF09): la ficha se le pide a
 * {@link VehicleService}, la interfaz pública de Vehículos, y no a su tabla. Va en la capa web y no
 * en el servicio porque la ficha es parte de la respuesta, no de la regla de negocio.
 *
 * <p>Usa {@code findModel} y no {@code getModel}: un modelo dado de baja no se puede elegir, pero
 * quien ya lo tenía lo sigue viendo en su perfil.
 */
@Component
public class UserProfileAssembler {

    private final VehicleService vehicleService;

    public UserProfileAssembler(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    public UserProfileResponse toResponse(User user) {
        VehicleModel vehicle = user.getVehicleModelId() == null
                ? null
                : vehicleService.findModel(user.getVehicleModelId()).orElse(null);
        return UserProfileResponse.fromDomain(user, vehicle);
    }

    /** Muchos perfiles con una sola consulta al catálogo, no una por usuario. */
    public List<UserProfileResponse> toResponses(List<User> users) {
        Map<Long, VehicleModel> vehicles = vehicleService.findModels(users.stream()
                .map(User::getVehicleModelId)
                .filter(Objects::nonNull)
                .toList());
        return users.stream()
                .map(user -> UserProfileResponse.fromDomain(
                        user, user.getVehicleModelId() == null ? null : vehicles.get(user.getVehicleModelId())))
                .toList();
    }
}
