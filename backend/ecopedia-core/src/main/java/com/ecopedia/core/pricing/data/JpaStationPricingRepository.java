package com.ecopedia.core.pricing.data;

import com.ecopedia.core.pricing.domain.StationPricing;
import com.ecopedia.core.pricing.domain.StationPricingRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Adaptador Spring Data JPA de {@link StationPricingRepository}. */
@Repository
public interface JpaStationPricingRepository extends JpaRepository<StationPricing, Long>, StationPricingRepository {}
