package com.ecopedia.charging.session.data;

import com.ecopedia.charging.session.domain.ConnectorState;
import com.ecopedia.charging.session.domain.ConnectorStateRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaConnectorStateRepository extends JpaRepository<ConnectorState, Long>, ConnectorStateRepository {}
