package com.ecopedia.core.pricing.domain;

import java.util.Optional;

/** Puerto DAO de {@link StationPricing}. */
public interface StationPricingRepository {

    StationPricing save(StationPricing pricing);

    Optional<StationPricing> findById(Long stationId);
}
