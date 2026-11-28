package com.ecopedia.charging.checkout.domain;

/**
 * El conductor debe una carga anterior (pendiente o vencida): no se reserva hasta que la salde (RF02,
 * ECO-62). 409 con el {@code code} {@code DRIVER_HAS_DEBT}: el front le dice que la pague desde Medios
 * de pago.
 */
public class DriverHasDebtException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DriverHasDebtException(Long driverId) {
        super("El conductor " + driverId + " tiene un pago pendiente de una carga anterior");
    }

    public DriverHasDebtException(Long driverId, Throwable cause) {
        super("El conductor " + driverId + " tiene un pago pendiente de una carga anterior", cause);
    }
}
