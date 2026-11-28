package com.ecopedia.core.vehicle.domain;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /**
     * Un modelo por id aunque esté dado de baja, para mostrar el auto que el conductor ya eligió.
     *
     * <p>No filtra los dados de baja, a propósito: que un modelo deje de ofrecerse no le borra el
     * auto del perfil a quien ya lo tenía. Usuarios guarda solo el id (RNF09) y resuelve la ficha
     * con esto.
     */
    Optional<VehicleModel> findModel(Long modelId);

    /** Los modelos de esos ids, también los dados de baja, por id: para armar muchos perfiles de una vez. */
    Map<Long, VehicleModel> findModels(Collection<Long> modelIds);
}
