package com.ecopedia.charging.checkout.data;

import com.ecopedia.charging.checkout.domain.PendingDepositRefund;
import com.ecopedia.charging.checkout.domain.PendingDepositRefundRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Adaptador Spring Data JPA de {@link PendingDepositRefundRepository}. */
@Repository
public interface JpaPendingDepositRefundRepository
        extends JpaRepository<PendingDepositRefund, Long>, PendingDepositRefundRepository {

    @Override
    List<PendingDepositRefund> findByRefundedAtIsNullOrderByCreatedAtAsc();
}
