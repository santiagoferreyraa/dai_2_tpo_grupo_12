package com.ecopedia.charging.booking.domain;

/**
 * El conductor ya tiene comprometido ese tiempo. Se responde 409.
 *
 * <p><b>Es la contracara de {@link SlotUnavailableException}, y son dos cosas distintas.</b>
 * Aquella dice que el CONECTOR está tomado —por cualquiera— y la salida es elegir otro horario u
 * otro conector. Esta dice que el CONDUCTOR está tomado: el conector puede estar perfectamente
 * libre, lo que no está libre es él. La salida es otra —esperar a que termine, o cancelar lo que
 * ya tiene— y por eso el front necesita poder distinguirlas, que es para qué está {@link #code()}.
 *
 * <p>La regla que hace cumplir: una reserva no bloquea solo el conector para el resto (RF08), sino
 * también al conductor mientras dura. Sin esto, el mismo conductor podía reservar dos conectores
 * para la misma hora —o uno más mientras su auto estaba cargando—, que es imposible en el mundo
 * real: tiene un solo auto y lo tiene enchufado en un solo lado.
 */
public class DriverAlreadyBookedException extends RuntimeException {

    private final String code;

    private DriverAlreadyBookedException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Un código estable para que el front elija las palabras.
     *
     * <p>El mensaje nombra ids y está escrito para el log; el conductor necesita otra frase, y una
     * distinta por caso: "esperá a que termine la carga" no es "ya tenés algo reservado a esa hora".
     */
    public String code() {
        return code;
    }

    /** Tiene una reserva corriendo ahora mismo: el auto está —o debería estar— enchufado. */
    public static DriverAlreadyBookedException charging(Long bookingId, Long driverId) {
        return new DriverAlreadyBookedException(
                "DRIVER_CHARGING",
                "El conductor " + driverId + " tiene la reserva " + bookingId
                        + " en curso: no puede reservar otra hasta que termine");
    }

    /** Tiene otra reserva que se cruza con la ventana pedida, acá o en cualquier otra estación. */
    public static DriverAlreadyBookedException overlapping(Long bookingId, Long driverId) {
        return new DriverAlreadyBookedException(
                "DRIVER_WINDOW_TAKEN",
                "El conductor " + driverId + " ya tiene la reserva " + bookingId
                        + " en un horario que se cruza con el pedido");
    }
}
