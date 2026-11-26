package com.ecopedia.async.stats.data;

import com.ecopedia.async.stats.domain.DemandSlot;
import com.ecopedia.async.stats.domain.DemandSlotRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data JPA para {@link DemandSlot} (ECO-301). */
public interface JpaDemandSlotRepository extends JpaRepository<DemandSlot, Long>, DemandSlotRepository {

    @Override
    @Query(
            """
            SELECT d FROM DemandSlot d
            WHERE d.slotStart >= :from AND d.slotStart < :to
            ORDER BY d.slotStart
            """)
    List<DemandSlot> findInRange(@Param("from") Instant from, @Param("to") Instant to);

    @Override
    @Query(
            """
            SELECT d FROM DemandSlot d
            WHERE d.stationOwnerId = :ownerId
              AND d.slotStart >= :from AND d.slotStart < :to
            ORDER BY d.slotStart
            """)
    List<DemandSlot> findByOwnerInRange(
            @Param("ownerId") Long ownerId, @Param("from") Instant from, @Param("to") Instant to);
}
