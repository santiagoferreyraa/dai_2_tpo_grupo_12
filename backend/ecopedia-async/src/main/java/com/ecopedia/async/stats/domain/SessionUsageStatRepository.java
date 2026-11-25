package com.ecopedia.async.stats.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de estadísticas de uso por sesión (ECO-301). */
public interface SessionUsageStatRepository {

    Optional<SessionUsageStat> findBySessionId(String sessionId);

    SessionUsageStat save(SessionUsageStat stat);

    List<SessionUsageStat> findInRange(Instant from, Instant to);

    List<SessionUsageStat> findByOwnerInRange(Long ownerId, Instant from, Instant to);
}
