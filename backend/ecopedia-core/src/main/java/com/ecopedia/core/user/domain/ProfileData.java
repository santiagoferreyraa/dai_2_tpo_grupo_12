package com.ecopedia.core.user.domain;

/**
 * Datos para actualizar el perfil de usuario.
 *
 * <p><b>Es un reemplazo de lo editable, no un parche.</b> Los dos campos se aplican siempre, y
 * eso vale sobre todo para {@code vehicleModelId}: en {@code null} significa "sacate el auto",
 * no "dejalo como estaba". Tratarlo como un parche —ignorar el nulo— dejaría sin forma de
 * borrarlo, que es una operación que el conductor tiene que poder hacer; y un campo con dos
 * significados según el resto del cuerpo es peor que uno que siempre quiere decir lo mismo.
 * Quien llame tiene que mandar el perfil entero, que es lo que hace la pantalla de edición.
 *
 * @param fullName el nombre a mostrar
 * @param vehicleModelId id del modelo del catálogo, o {@code null} para quedar sin auto
 */
public record ProfileData(String fullName, Long vehicleModelId) {}
