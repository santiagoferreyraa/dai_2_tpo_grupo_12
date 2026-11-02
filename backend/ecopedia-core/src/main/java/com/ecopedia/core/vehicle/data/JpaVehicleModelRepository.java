package com.ecopedia.core.vehicle.data;

import com.ecopedia.core.vehicle.domain.VehicleModel;
import com.ecopedia.core.vehicle.domain.VehicleModelRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaVehicleModelRepository extends JpaRepository<VehicleModel, Long>, VehicleModelRepository {

    @Override
    List<VehicleModel> findByActiveTrueOrderByBrandAscNameAsc();
}
