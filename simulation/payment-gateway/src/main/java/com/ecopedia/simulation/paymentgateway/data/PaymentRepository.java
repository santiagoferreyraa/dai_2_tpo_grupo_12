package com.ecopedia.simulation.paymentgateway.data;

import com.ecopedia.simulation.paymentgateway.domain.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    /** El cobro que ya se hizo con esa clave, si se hizo: un reintento lo recibe en vez de cobrar dos veces. */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
