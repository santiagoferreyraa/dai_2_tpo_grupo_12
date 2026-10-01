package com.ecopedia.core.user.web.dto;

import com.ecopedia.core.user.domain.ProfileData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lo que se puede cambiar del propio perfil (RF01): el nombre y el auto.
 *
 * <p><b>La lista corta es la regla de negocio.</b> El email identifica al usuario y viaja adentro
 * del token: cambiarlo por acá dejaría al que está logueado con una sesión emitida a nombre de
 * una cuenta que ya no existe. El rol es una decisión administrativa, igual que en el alta —si se
 * aceptara acá, cualquiera se promovería a {@code ADMIN} desde la pantalla de perfil y las
 * anotaciones del sistema dejarían de valer—. Y la baja tiene su propia operación, que es del
 * administrador.
 *
 * <p>Un cuerpo que igual mande {@code "email"} o {@code "role"} no falla: Jackson descarta las
 * propiedades que el record no declara.
 *
 * <p><b>{@code vehicleModelId} ausente y {@code vehicleModelId: null} son lo mismo, y los dos
 * dejan al conductor sin auto.</b> Jackson no distingue una propiedad que no vino de una que vino
 * en nulo, así que el {@code PUT} reemplaza el perfil editable entero en vez de parcharlo. Es la
 * única forma de que sacarse el auto sea posible sin inventar un centinela —un {@code -1} que
 * quiera decir "borralo"— o un endpoint aparte para una sola columna. La pantalla de edición manda
 * siempre los dos campos; ver {@code ProfileData}.
 *
 * <p>El largo máximo del nombre es el de la columna. Sin esta anotación, uno de más de 120
 * caracteres no lo rechaza la validación sino la base, y eso llega al usuario como un 500.
 *
 * <p>El id del modelo NO se valida acá más allá del tipo: que exista y que siga ofreciéndose es
 * una pregunta que solo el catálogo puede contestar, y la contesta {@code UserServiceImpl} al
 * resolverlo. Una anotación no puede consultar la base.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "El nombre completo es obligatorio")
                @Size(max = 120, message = "El nombre completo no puede superar los 120 caracteres")
                String fullName,
        Long vehicleModelId) {

    public ProfileData toDomainData() {
        return new ProfileData(fullName, vehicleModelId);
    }
}
