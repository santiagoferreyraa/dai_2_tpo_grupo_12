package com.ecopedia.integration.payment.domain;

/**
 * Puerto secundario hacia Reservas (ecopedia-charging), para la cuenta marcada por deuda (ECO-62).
 *
 * <p>Cuando fallan los tres reintentos de una deuda, Pagos le pide a Reservas que anule las reservas
 * confirmadas del conductor. Va por la ruta interna de Reservas con la clave de servicio: no es un
 * pedido del conductor, y ningún conductor tiene que poder anularle las reservas a otro.
 */
public interface DriverBookingsPort {

    /**
     * Anula las reservas confirmadas del conductor, con la seña devuelta. Idempotente: pedirlo dos
     * veces anula una sola.
     *
     * @return cuántas reservas se anularon
     * @throws BookingsUnavailableException si Reservas no contestó; el barrido lo vuelve a pedir
     */
    int cancelConfirmedBookingsForDebt(Long driverId);
}
