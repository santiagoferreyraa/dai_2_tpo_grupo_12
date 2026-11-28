package com.ecopedia.core.vehicle.service;

import com.ecopedia.core.vehicle.domain.VehicleModel;
import com.ecopedia.core.vehicle.domain.VehicleModelNotFoundException;
import com.ecopedia.core.vehicle.domain.VehicleModelRepository;
import com.ecopedia.core.vehicle.domain.VehicleService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VehicleServiceImpl implements VehicleService {

    private final VehicleModelRepository modelRepository;

    public VehicleServiceImpl(VehicleModelRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    @Override
    public List<VehicleModel> listAvailableModels() {
        return modelRepository.findByActiveTrueOrderByBrandAscNameAsc();
    }

    /**
     * <p><b>Un modelo dado de baja no se puede elegir, pero el que ya lo tiene lo conserva.</b>
     * Esta operación es la que atiende la elección, así que acá el modelo inactivo es un
     * rechazo. Leer el perfil de alguien que lo eligió antes no pasa por acá: la relación JPA
     * carga la fila sin preguntar si sigue ofreciéndose, que es justo lo que hace que la baja
     * sea lógica y no una forma lenta de borrar.
     */
    @Override
    public VehicleModel getModel(Long modelId) {
        return modelRepository
                .findById(modelId)
                .filter(VehicleModel::isActive)
                .orElseThrow(() -> new VehicleModelNotFoundException(modelId));
    }

    @Override
    public Optional<VehicleModel> findModel(Long modelId) {
        return modelRepository.findById(modelId);
    }

    @Override
    public Map<Long, VehicleModel> findModels(Collection<Long> modelIds) {
        if (modelIds.isEmpty()) return Map.of();
        return modelRepository.findAllById(Set.copyOf(modelIds)).stream()
                .collect(Collectors.toMap(VehicleModel::getId, Function.identity()));
    }
}
