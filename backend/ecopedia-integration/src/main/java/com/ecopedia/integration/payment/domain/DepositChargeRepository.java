package com.ecopedia.integration.payment.domain;

import java.util.Optional;

/** Puerto DAO de persistencia de {@link DepositCharge}. */
public interface DepositChargeRepository {

    DepositCharge save(DepositCharge charge);

    Optional<DepositCharge> findById(Long id);

    Optional<DepositCharge> findByIdempotencyKey(String idempotencyKey);
}
