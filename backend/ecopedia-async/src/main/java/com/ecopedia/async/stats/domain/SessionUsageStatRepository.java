package com.ecopedia.async.stats.domain;

import java.util.Optional;

/**
 * Puerto de persistencia del cursor de cada sesión (ECO-301).
 *
 * <p>El tablero no lee de acá: lee las franjas de {@link DemandSlotRepository}. Esto guarda el último
 * acumulado de cada sesión para poder repartir la energía entre franjas.
 */
public interface SessionUsageStatRepository {

    Optional<SessionUsageStat> findBySessionId(String sessionId);

    SessionUsageStat save(SessionUsageStat stat);
}
