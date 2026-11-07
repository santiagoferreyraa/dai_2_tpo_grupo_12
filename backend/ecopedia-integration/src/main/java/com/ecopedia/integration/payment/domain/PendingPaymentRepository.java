package com.ecopedia.integration.payment.domain;

import java.util.List;
import java.util.Optional;

/**
 * Puerto DAO de persistencia para deudas y pagos pendientes (RF02, RF14, ECO-54).
 */
public interface PendingPaymentRepository {

    PendingPayment save(PendingPayment pendingPayment);

    Optional<PendingPayment> findById(Long id);

    List<PendingPayment> findByDriverIdAndStatus(Long driverId, PendingPaymentStatus status);

    boolean existsByDriverIdAndStatus(Long driverId, PendingPaymentStatus status);
}
