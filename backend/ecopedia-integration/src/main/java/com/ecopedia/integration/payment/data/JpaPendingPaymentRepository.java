package com.ecopedia.integration.payment.data;

import com.ecopedia.integration.payment.domain.PendingPayment;
import com.ecopedia.integration.payment.domain.PendingPaymentRepository;
import com.ecopedia.integration.payment.domain.PendingPaymentStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador Spring Data JPA de {@link PendingPaymentRepository} para persistencia en base de datos.
 */
@Repository
public interface JpaPendingPaymentRepository extends JpaRepository<PendingPayment, Long>, PendingPaymentRepository {

    @Override
    Optional<PendingPayment> findByReference(String reference);

    @Override
    List<PendingPayment> findByDriverIdAndStatus(Long driverId, PendingPaymentStatus status);

    @Override
    boolean existsByDriverIdAndStatus(Long driverId, PendingPaymentStatus status);

    @Override
    boolean existsByDriverIdAndStatusIn(Long driverId, Collection<PendingPaymentStatus> statuses);

    @Override
    List<PendingPayment> findByDriverIdAndStatusInOrderByCreatedAtAsc(
            Long driverId, Collection<PendingPaymentStatus> statuses);

    @Override
    @Query("select p from PendingPayment p where p.status = com.ecopedia.integration.payment.domain"
            + ".PendingPaymentStatus.PENDING and (p.nextAttemptAt is null or p.nextAttemptAt <= :now)"
            + " order by p.createdAt")
    List<PendingPayment> findDueForRetry(@Param("now") Instant now);

    @Override
    @Query("select p from PendingPayment p where p.status = com.ecopedia.integration.payment.domain"
            + ".PendingPaymentStatus.DEFAULTED and p.bookingsCancelled = false order by p.createdAt")
    List<PendingPayment> findDefaultedWithBookingsToCancel();

    /*
     * Una sola sentencia condicional, no leer y guardar: dos llamadores que leen a la vez "sin reclamo"
     * y guardan los dos ganarían los dos. Acá la base decide: la segunda actualización no encuentra la
     * fila en condiciones y devuelve 0.
     */
    @Modifying
    @Transactional
    @Query("update PendingPayment p set p.claimedUntil = :until where p.id = :id"
            + " and p.status in (com.ecopedia.integration.payment.domain.PendingPaymentStatus.PENDING,"
            + " com.ecopedia.integration.payment.domain.PendingPaymentStatus.DEFAULTED)"
            + " and (p.claimedUntil is null or p.claimedUntil < :now)")
    int claimRow(@Param("id") Long id, @Param("now") Instant now, @Param("until") Instant until);

    @Override
    default boolean claim(Long id, Instant now, Instant until) {
        return claimRow(id, now, until) == 1;
    }

    @Override
    @Modifying
    @Transactional
    @Query("update PendingPayment p set p.claimedUntil = null where p.id = :id")
    void releaseClaim(@Param("id") Long id);
}
