package com.ecopedia.core.vehicle.domain;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de persistencia del catálogo de modelos.
 *
 * <p>La interfaz vive en la capa de negocio y no en la de datos, igual que
 * {@code ConnectorRepository}: es el negocio el que declara qué necesita saber de la base, y la
 * implementación JPA la que se acomoda. Así la capa de negocio no depende de Spring Data.
 */
public interface VehicleModelRepository {

    Optional<VehicleModel> findById(Long modelId);

    /** El catálogo ofrecible, ordenado como se muestra: por marca y dentro de ella por modelo. */
    List<VehicleModel> findByActiveTrueOrderByBrandAscNameAsc();
}
