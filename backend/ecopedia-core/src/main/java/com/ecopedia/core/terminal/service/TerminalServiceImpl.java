package com.ecopedia.core.terminal.service;

import com.ecopedia.core.terminal.domain.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TerminalServiceImpl implements TerminalService {

    private final StationRepository stationRepository;
    private final ConnectorRepository connectorRepository;

    public TerminalServiceImpl(StationRepository stationRepository, ConnectorRepository connectorRepository) {
        this.stationRepository = stationRepository;
        this.connectorRepository = connectorRepository;
    }

    @Override
    public Station createStation(Long ownerId, StationData data) {
        Station station = new Station();
        station.setName(data.name());
        station.setAddress(data.address());
        station.setLatitude(data.latitude());
        station.setLongitude(data.longitude());
        station.setPhotoUrls(data.photoUrls() != null ? data.photoUrls() : new ArrayList<>());
        station.setOwnerId(ownerId);
        station.setActive(true);
        Station saved = stationRepository.save(station);
        Hibernate.initialize(saved.getPhotoUrls());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isStationOwner(Long stationId, Long userId) {
        return stationRepository
                .findById(stationId)
                .map(station -> station.getOwnerId().equals(userId))
                .orElse(false);
    }

    /*
     * Adentro de la transacción a propósito: la estación del conector es LAZY, y fuera de acá
     * leerle el dueño explotaría con LazyInitializationException.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean isConnectorOwner(Long connectorId, Long userId) {
        return connectorRepository
                .findById(connectorId)
                .map(connector -> connector.getStation().getOwnerId().equals(userId))
                .orElse(false);
    }

    @Override
    public Station updateStation(Long stationId, StationData data) {
        Station station = stationRepository
                .findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException("Estación no encontrada con ID: " + stationId));

        station.setName(data.name());
        station.setAddress(data.address());
        station.setLatitude(data.latitude());
        station.setLongitude(data.longitude());
        if (data.photoUrls() != null) {
            station.setPhotoUrls(data.photoUrls());
        }
        Station saved = stationRepository.save(station);
        Hibernate.initialize(saved.getPhotoUrls());
        return saved;
    }

    @Override
    public void deactivateStation(Long stationId) {
        Station station = stationRepository
                .findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException("Estación no encontrada con ID: " + stationId));
        station.setActive(false);
        stationRepository.save(station);
    }

    @Override
    public Connector addConnector(Long stationId, ConnectorType connectorType, BigDecimal maxPowerKw) {
        Station station = stationRepository
                .findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException("Estación no encontrada con ID: " + stationId));

        Connector connector = new Connector();
        connector.setStation(station);
        connector.setConnectorType(connectorType);
        connector.setMaxPowerKw(maxPowerKw);
        // Nace disponible: el operador lo saca de servicio después si hace falta.
        connector.setOperationalStatus(OperationalStatus.AVAILABLE);
        return connectorRepository.save(connector);
    }

    @Override
    public Connector configureConnector(Long connectorId, ConnectorType connectorType, BigDecimal maxPowerKw) {
        Connector connector = connectorRepository
                .findById(connectorId)
                .orElseThrow(() -> new IllegalArgumentException("Conector no encontrado con ID: " + connectorId));

        connector.setConnectorType(connectorType);
        connector.setMaxPowerKw(maxPowerKw);
        return connectorRepository.save(connector);
    }

    @Override
    public void changeOperationalStatus(Long connectorId, OperationalStatus operationalStatus) {
        Connector connector = connectorRepository
                .findById(connectorId)
                .orElseThrow(() -> new IllegalArgumentException("Conector no encontrado con ID: " + connectorId));

        connector.setOperationalStatus(operationalStatus);
        connectorRepository.save(connector);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StationResult> search(SearchCriteria criteria) {
        List<Station> activeStations = stationRepository.findAllActive();
        List<StationResult> results = new ArrayList<>();

        for (Station station : activeStations) {
            double distanceKm = calculateHaversineDistanceKm(
                    criteria.latitude(), criteria.longitude(),
                    station.getLatitude(), station.getLongitude());

            if (distanceKm > criteria.radiusKm()) {
                continue;
            }

            List<Connector> connectors = connectorRepository.findByStationId(station.getId());
            List<StationResult.ConnectorSummary> matchingConnectors = new ArrayList<>();

            for (Connector connector : connectors) {
                if (criteria.connectorType() != null && connector.getConnectorType() != criteria.connectorType()) {
                    continue;
                }
                if (criteria.minimumPowerKw() != null
                        && connector.getMaxPowerKw().compareTo(criteria.minimumPowerKw()) < 0) {
                    continue;
                }
                if (criteria.onlyAvailable() && connector.getOperationalStatus() != OperationalStatus.AVAILABLE) {
                    continue;
                }

                matchingConnectors.add(new StationResult.ConnectorSummary(
                        connector.getId(),
                        connector.getConnectorType(),
                        connector.getMaxPowerKw(),
                        connector.getOperationalStatus()));
            }

            /*
             * Una estación sin conectores que sirvan no es un resultado de búsqueda.
             *
             * Hasta acá el filtro solo recortaba la lista de conectores, y la estación entraba
             * igual con la lista vacía. O sea que pedir CHADEMO devolvía las estaciones que no
             * tienen ninguno, y `onlyAvailable` devolvía las que están enteras fuera de
             * servicio. Quien busca dónde cargar recibía lugares donde no puede cargar.
             *
             * También cubre el caso sin filtros: una estación dada de alta a la que todavía no
             * le cargaron conectores no es un lugar donde enchufar. El ABM no la pierde, porque
             * su listado sale de getAllStations y no de acá.
             */
            if (matchingConnectors.isEmpty()) {
                continue;
            }

            results.add(new StationResult(
                    station.getId(),
                    station.getName(),
                    station.getAddress(),
                    station.getLatitude(),
                    station.getLongitude(),
                    distanceKm,
                    matchingConnectors));
        }

        results.sort(Comparator.comparingDouble(StationResult::distanceKm));
        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public Connector getConnector(Long connectorId) {
        return connectorRepository
                .findById(connectorId)
                .orElseThrow(() -> new IllegalArgumentException("Conector no encontrado con ID: " + connectorId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Connector> getAllConnectors() {
        return connectorRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Connector> getConnectorsByStation(Long stationId) {
        return connectorRepository.findByStationId(stationId);
    }

    /*
     * Las fotos se cargan a mano antes de devolver la estacion.
     *
     * `photoUrls` es una @ElementCollection, o sea LAZY, y quien la lee es el mapeo a
     * StationResponse, que corre en el controlador: con `open-in-view: false` ahi ya no hay
     * sesion abierta y el acceso explota con LazyInitializationException. Sin estas dos
     * lineas, GET /api/stations y GET /api/stations/{id} responden 500 apenas hay una
     * estacion cargada.
     */
    @Override
    @Transactional(readOnly = true)
    public Station getStation(Long stationId) {
        Station station = stationRepository
                .findById(stationId)
                .orElseThrow(() -> new IllegalArgumentException("Estación no encontrada con ID: " + stationId));

        Hibernate.initialize(station.getPhotoUrls());
        return station;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Station> getAllStations() {
        List<Station> stations = stationRepository.findAllActive();
        stations.forEach(station -> Hibernate.initialize(station.getPhotoUrls()));
        return stations;
    }

    @Override
    public void removeConnector(Long connectorId) {
        Connector connector = connectorRepository
                .findById(connectorId)
                .orElseThrow(() -> new IllegalArgumentException("Conector no encontrado con ID: " + connectorId));
        connectorRepository.delete(connector);
    }

    private static double calculateHaversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
