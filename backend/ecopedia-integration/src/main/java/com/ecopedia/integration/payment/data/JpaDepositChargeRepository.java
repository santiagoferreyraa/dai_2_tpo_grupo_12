package com.ecopedia.integration.payment.data;

import com.ecopedia.integration.payment.domain.DepositCharge;
import com.ecopedia.integration.payment.domain.DepositChargeRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Adaptador Spring Data JPA de {@link DepositChargeRepository}, igual que el de las tarjetas. */
@Repository
public interface JpaDepositChargeRepository extends JpaRepository<DepositCharge, Long>, DepositChargeRepository {

    @Override
    Optional<DepositCharge> findByIdempotencyKey(String idempotencyKey);
}
