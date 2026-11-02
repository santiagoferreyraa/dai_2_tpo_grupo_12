package com.ecopedia.simulation.paymentgateway.data;

import com.ecopedia.simulation.paymentgateway.domain.Refund;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, String> {

    /** Lo mismo que en los cobros: con la misma clave, el mismo reintegro, no uno nuevo. */
    Optional<Refund> findByIdempotencyKey(String idempotencyKey);
}
