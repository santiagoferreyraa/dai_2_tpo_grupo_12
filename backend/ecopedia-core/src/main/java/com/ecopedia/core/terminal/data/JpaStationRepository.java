package com.ecopedia.core.terminal.data;

import com.ecopedia.core.terminal.domain.Station;
import com.ecopedia.core.terminal.domain.StationRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaStationRepository extends JpaRepository<Station, Long>, StationRepository {

    @Override
    @Query("SELECT DISTINCT s FROM Station s LEFT JOIN FETCH s.photoUrls WHERE s.active = true")
    List<Station> findAllActive();

    @Override
    @Query("SELECT DISTINCT s FROM Station s LEFT JOIN FETCH s.photoUrls WHERE s.active = true"
            + " AND s.latitude BETWEEN :minLatitude AND :maxLatitude"
            + " AND s.longitude BETWEEN :minLongitude AND :maxLongitude")
    List<Station> findActiveWithin(
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("minLongitude") double minLongitude,
            @Param("maxLongitude") double maxLongitude);
}
