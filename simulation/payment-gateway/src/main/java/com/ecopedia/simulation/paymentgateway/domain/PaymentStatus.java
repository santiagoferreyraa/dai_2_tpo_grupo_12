package com.ecopedia.simulation.paymentgateway.domain;

/** Los estados de un cobro, con los nombres que usa Mercado Pago en su API. */
public enum PaymentStatus {
    APPROVED,
    REJECTED,
    /** Aprobado y devuelto entero. Un reintegro parcial lo deja en {@code approved}. */
    REFUNDED;

    public String apiValue() {
        return name().toLowerCase();
    }
}
