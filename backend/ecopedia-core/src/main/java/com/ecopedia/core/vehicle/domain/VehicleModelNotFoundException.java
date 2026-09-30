package com.ecopedia.core.vehicle.domain;

/**
 * El id de modelo que llegó no existe en el catálogo, o existe pero está dado de baja.
 *
 * <p>Excepción propia y no {@code IllegalArgumentException} para que quien la atrape sepa qué
 * pasó sin leer el texto del mensaje. Hoy la única forma de llegar acá es eligiendo el auto
 * desde el perfil, donde es un 400 —el cuerpo del pedido trae un id que no sirve—; el día que un
 * endpoint pida un modelo por su id, ahí el mismo caso es un 404 y quien lo escriba puede
 * distinguirlo sin tocar esta clase.
 */
public class VehicleModelNotFoundException extends RuntimeException {

    public VehicleModelNotFoundException(Long modelId) {
        super("No existe un modelo de vehículo con ID: " + modelId);
    }
}
