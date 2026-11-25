package com.ecopedia.charging.checkout.domain;

import java.util.List;

/** Puerto DAO de las señas cobradas sin reserva que esperan devolución (ECO-62). */
public interface PendingDepositRefundRepository {

    PendingDepositRefund save(PendingDepositRefund refund);

    /**
     * Las que todavía hay que pedir, de la más vieja a la más nueva: ni devueltas ni rechazadas por Pagos.
     */
    List<PendingDepositRefund> findByRefundedAtIsNullAndFailedAtIsNullOrderByCreatedAtAsc();
}
