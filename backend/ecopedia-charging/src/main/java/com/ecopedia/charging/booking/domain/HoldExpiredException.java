package com.ecopedia.charging.booking.domain;

import java.util.UUID;

/**
 * La retención existió pero se venció antes de que el conductor confirmara. Se responde 410.
 *
 * <p>410 y no 404, y la diferencia le importa al front: 404 es "ese id nunca fue nada" —un error
 * de programación— y 410 es "se te acabó el tiempo", que es lo único que hay que mostrarle al
 * conductor, junto con el botón para volver a elegir el mismo slot.
 */
public class HoldExpiredException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public HoldExpiredException(UUID holdId) {
        super("La retención " + holdId + " venció: volvé a elegir el horario");
    }
}
