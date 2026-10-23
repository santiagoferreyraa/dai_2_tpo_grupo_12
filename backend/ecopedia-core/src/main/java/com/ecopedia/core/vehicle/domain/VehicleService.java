package com.ecopedia.core.vehicle.domain;

import java.util.List;

/**
 * Interfaz pública del componente {@code Vehiculos} (stateless).
 *
 * <p><b>Es solo de lectura, y esa es la decisión.</b> El catálogo es cerrado: los modelos entran
 * por migración, no por pantalla. Si hubiera un alta acá, el conductor que no encuentra su auto
 * terminaría cargando uno con los números que le parezcan, y esos números son los que después
 * filtran estaciones y estiman cuánto tarda una carga. Un catálogo que cualquiera puede
 * completar deja de ser una fuente de verdad y pasa a ser un campo de texto con más pasos.
 */
public interface VehicleService {

    /** Los modelos que se le pueden ofrecer al conductor, ordenados por marca y modelo. */
    List<VehicleModel> listAvailableModels();

    /**
     * Un modelo por id, para resolver el que eligió el conductor.
     *
     * @throws VehicleModelNotFoundException si el id no existe o el modelo está dado de baja
     */
    VehicleModel getModel(Long modelId);
}
