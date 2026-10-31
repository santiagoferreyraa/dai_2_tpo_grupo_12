package com.ecopedia.charging.booking.domain;

/**
 * Qué pasó con la seña de una reserva.
 *
 * <p>Es un estado aparte del de la reserva porque las dos cosas se mueven distinto: una reserva
 * cancelada puede tener la seña devuelta, por devolver o perdida, según cuándo se canceló.
 */
public enum DepositStatus {
    /** La reserva no tuvo seña: el esquema tarifario no la pide, o es anterior a ECO-53. */
    NONE,
    /** Cobrada y retenida mientras la reserva sigue en pie. */
    CHARGED,
    /**
     * Corresponde devolverla y todavía no se pudo: el conductor canceló a tiempo y la devolución
     * espera a Pagos. La reintenta {@code DepositRefundProcessor}.
     */
    REFUND_PENDING,
    /** Devuelta a la tarjeta. */
    REFUNDED,
    /** Quedó para el operador: el conductor no se presentó o canceló fuera de plazo. */
    FORFEITED
}
