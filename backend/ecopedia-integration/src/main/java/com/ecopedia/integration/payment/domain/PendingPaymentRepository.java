package com.ecopedia.integration.payment.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Puerto DAO de persistencia para deudas y pagos pendientes (RF02, RF14, ECO-54, ECO-62).
 */
public interface PendingPaymentRepository {

    PendingPayment save(PendingPayment pendingPayment);

    Optional<PendingPayment> findById(Long id);

    Optional<PendingPayment> findByReference(String reference);

    List<PendingPayment> findByDriverIdAndStatus(Long driverId, PendingPaymentStatus status);

    boolean existsByDriverIdAndStatus(Long driverId, PendingPaymentStatus status);

    /** Si el conductor debe algo: pendiente o vencido. */
    boolean existsByDriverIdAndStatusIn(Long driverId, Collection<PendingPaymentStatus> statuses);

    /** Lo que el conductor debe, de la más vieja a la más nueva. */
    List<PendingPayment> findByDriverIdAndStatusInOrderByCreatedAtAsc(
            Long driverId, Collection<PendingPaymentStatus> statuses);

    /** Las deudas pendientes cuyo reintento ya tocó (o que no tienen fecha: las de antes de ECO-62). */
    List<PendingPayment> findDueForRetry(Instant now);

    /** Las vencidas cuyas reservas todavía no se anularon: Reservas no contestó la primera vez. */
    List<PendingPayment> findDefaultedWithBookingsToCancel();

    /**
     * Reclama la deuda para cobrarla, si sigue abierta y nadie más la tiene reclamada: el barrido y el
     * conductor desde el perfil no pueden cobrarla los dos a la vez.
     *
     * @return {@code true} si este llamador se quedó con la deuda hasta {@code until}
     */
    boolean claim(Long id, Instant now, Instant until);

    /** Suelta el reclamo, haya salido o no el cobro. */
    void releaseClaim(Long id);
}
