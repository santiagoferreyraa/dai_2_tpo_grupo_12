package com.ecopedia.async.stats.data;

import com.ecopedia.async.stats.domain.SessionUsageStat;
import com.ecopedia.async.stats.domain.SessionUsageStatRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data JPA para {@link SessionUsageStat} (ECO-301). */
public interface JpaSessionUsageStatRepository
        extends JpaRepository<SessionUsageStat, Long>, SessionUsageStatRepository {}
