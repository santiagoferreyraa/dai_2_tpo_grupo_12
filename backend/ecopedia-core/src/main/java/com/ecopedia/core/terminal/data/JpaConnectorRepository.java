package com.ecopedia.core.terminal.data;

import com.ecopedia.core.terminal.domain.Connector;
import com.ecopedia.core.terminal.domain.ConnectorRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaConnectorRepository extends JpaRepository<Connector, Long>, ConnectorRepository {

    @Override
    List<Connector> findByStationId(Long stationId);

    @Override
    Optional<Connector> findByAccessCode(String accessCode);

    @Override
    boolean existsByAccessCode(String accessCode);
}
