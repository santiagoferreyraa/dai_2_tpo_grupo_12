package com.ecopedia.simulation.paymentgateway.data;

import com.ecopedia.simulation.paymentgateway.domain.CardToken;
import org.springframework.data.jpa.repository.JpaRepository;

/** Los tokens emitidos. Se buscan solo por id: es lo único que la pasarela le da a quien cobra. */
public interface CardTokenRepository extends JpaRepository<CardToken, String> {}
