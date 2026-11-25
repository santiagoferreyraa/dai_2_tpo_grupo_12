package com.ecopedia.async.stats.data;

import com.ecopedia.async.stats.domain.SessionUsageStat;
import com.ecopedia.async.stats.domain.SessionUsageStatRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data JPA para {@link SessionUsageStat} (ECO-301). */
public interface JpaSessionUsageStatRepository
        extends JpaRepository<SessionUsageStat, Long>, SessionUsageStatRepository {

    @Override
    @Query(
            """
            SELECT s FROM SessionUsageStat s
            WHERE s.startedAt >= :from AND s.startedAt <= :to
            ORDER BY s.lastReadingAt DESC
            """)
    List<SessionUsageStat> findInRange(@Param("from") Instant from, @Param("to") Instant to);

    @Override
    @Query(
            """
            SELECT s FROM SessionUsageStat s
            WHERE s.stationOwnerId = :ownerId
              AND s.startedAt >= :from AND s.startedAt <= :to
            ORDER BY s.lastReadingAt DESC
            """)
    List<SessionUsageStat> findByOwnerInRange(
            @Param("ownerId") Long ownerId, @Param("from") Instant from, @Param("to") Instant to);
}
