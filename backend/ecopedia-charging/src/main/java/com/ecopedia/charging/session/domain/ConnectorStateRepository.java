package com.ecopedia.charging.session.domain;

import java.util.Optional;

public interface ConnectorStateRepository {

    ConnectorState save(ConnectorState connectorState);

    Optional<ConnectorState> findById(Long connectorId);

    void deleteById(Long connectorId);
}
