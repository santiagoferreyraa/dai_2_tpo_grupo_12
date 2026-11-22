package com.ecopedia.charging.checkout.domain;

import java.util.List;

/** Puerto DAO de las señas cobradas sin reserva que esperan devolución (ECO-62). */
public interface PendingDepositRefundRepository {

    PendingDepositRefund save(PendingDepositRefund refund);

    /** Las que todavía no se devolvieron, de la más vieja a la más nueva. */
    List<PendingDepositRefund> findByRefundedAtIsNullOrderByCreatedAtAsc();
}
