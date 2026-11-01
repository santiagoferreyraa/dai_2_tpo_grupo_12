package com.ecopedia.core.pricing.domain;

import java.util.Optional;

/** Puerto DAO de {@link NetworkRates}: una sola fila. */
public interface NetworkRatesRepository {

    NetworkRates save(NetworkRates rates);

    Optional<NetworkRates> findById(Integer id);
}
