package com.ecopedia.charging.booking.domain;

/** La ventana pedida se cruza con otra que ya está comprometida. Se responde 409. */
public class SlotUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SlotUnavailableException(Long connectorId) {
        super("El conector " + connectorId + " ya tiene retenida o reservada una ventana que se cruza con la pedida");
    }
}
