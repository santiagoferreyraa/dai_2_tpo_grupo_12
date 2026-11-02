package com.ecopedia.core.pricing.data;

import com.ecopedia.core.pricing.domain.NetworkRates;
import com.ecopedia.core.pricing.domain.NetworkRatesRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Adaptador Spring Data JPA de {@link NetworkRatesRepository}. */
@Repository
public interface JpaNetworkRatesRepository extends JpaRepository<NetworkRates, Integer>, NetworkRatesRepository {}
