package com.ecopedia.integration.payment.data;

import com.ecopedia.integration.payment.domain.PendingPayment;
import com.ecopedia.integration.payment.domain.PendingPaymentRepository;
import com.ecopedia.integration.payment.domain.PendingPaymentStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Adaptador Spring Data JPA de {@link PendingPaymentRepository} para persistencia en base de datos.
 */
@Repository
public interface JpaPendingPaymentRepository extends JpaRepository<PendingPayment, Long>, PendingPaymentRepository {

    @Override
    List<PendingPayment> findByDriverIdAndStatus(Long driverId, PendingPaymentStatus status);

    @Override
    boolean existsByDriverIdAndStatus(Long driverId, PendingPaymentStatus status);
}
