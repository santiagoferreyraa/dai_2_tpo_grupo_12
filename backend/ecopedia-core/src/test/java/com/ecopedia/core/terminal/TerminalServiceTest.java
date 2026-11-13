package com.ecopedia.core.terminal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ecopedia.core.shared.ResourceNotFoundException;
import com.ecopedia.core.terminal.domain.*;
import com.ecopedia.core.terminal.service.TerminalServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerminalServiceTest {

    @Mock
    private StationRepository stationRepository;

    @Mock
    private ConnectorRepository connectorRepository;

    @InjectMocks
    private TerminalServiceImpl terminalService;

    private Station mockStation;
    private Connector mockConnector;

    @BeforeEach
    void setUp() {
        mockStation = new Station();
        mockStation.setId(10L);
        mockStation.setName("Estación UADE Obelisco");
        mockStation.setAddress("Lima 775, CABA");
        mockStation.setLatitude(-34.61315);
        mockStation.setLongitude(-58.38138);
        mockStation.setActive(true);

        mockConnector = new Connector();
        mockConnector.setId(1L);
        mockConnector.setStation(mockStation);
        mockConnector.setConnectorType(ConnectorType.CCS2);
        mockConnector.setMaxPowerKw(new BigDecimal("50.00"));
        mockConnector.setOperationalStatus(OperationalStatus.AVAILABLE);
    }

    @Test
    void testCreateStation() {
        when(stationRepository.save(any(Station.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<String> photos = List.of("https://cdn.ecopedia.test/st1.jpg");
        StationData data = new StationData(
                "Estación UADE Obelisco", "Lima 775, CABA", -34.61315, -58.38138, photos, "CABA-CENTRO");
        Station created = terminalService.createStation(7L, data);

        assertEquals(7L, created.getOwnerId());
        assertEquals("Estación UADE Obelisco", created.getName());
        assertEquals("Lima 775, CABA", created.getAddress());
        assertEquals(-34.61315, created.getLatitude());
        assertEquals(-58.38138, created.getLongitude());
        assertEquals("CABA-CENTRO", created.getNodeId());
        assertEquals(photos, created.getPhotoUrls());
        assertTrue(created.isActive());

        StationData withoutPhotos = new StationData("Sin Fotos", "Av. Corrientes 100", -34.60, -58.38, null, null);
        Station createdWithoutPhotos = terminalService.createStation(8L, withoutPhotos);
        assertEquals(8L, createdWithoutPhotos.getOwnerId());
        assertTrue(createdWithoutPhotos.getPhotoUrls().isEmpty());
        assertNull(createdWithoutPhotos.getNodeId());
        assertTrue(createdWithoutPhotos.isActive());

        verify(stationRepository, times(2)).save(any(Station.class));
    }

    @Test
    void testDeactivateStation() {
        when(stationRepository.findById(10L)).thenReturn(Optional.of(mockStation));

        terminalService.deactivateStation(10L);

        assertFalse(mockStation.isActive());
        verify(stationRepository, times(1)).save(mockStation);
    }

    @Test
    void testConfigureConnector() {
        when(connectorRepository.findById(1L)).thenReturn(Optional.of(mockConnector));
        when(connectorRepository.save(any(Connector.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Connector updated = terminalService.configureConnector(1L, ConnectorType.TYPE_2, new BigDecimal("22.00"));

        assertSame(mockConnector, updated);
        assertEquals(ConnectorType.TYPE_2, updated.getConnectorType());
        assertEquals(new BigDecimal("22.00"), updated.getMaxPowerKw());
        verify(connectorRepository, times(1)).save(mockConnector);
    }

    @Test
    void testAddConnector() {
        when(stationRepository.findById(10L)).thenReturn(Optional.of(mockStation));
        when(connectorRepository.save(any(Connector.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Connector created = terminalService.addConnector(10L, ConnectorType.CHADEMO, new BigDecimal("100.00"));

        assertSame(mockStation, created.getStation());
        assertEquals(ConnectorType.CHADEMO, created.getConnectorType());
        assertEquals(new BigDecimal("100.00"), created.getMaxPowerKw());
        assertEquals(OperationalStatus.AVAILABLE, created.getOperationalStatus());
    }

    /**
     * El caso que la operación ambigua anterior no podía cubrir: los identificadores de
     * estación y de conector son secuencias independientes, así que una operación que
     * aceptara cualquiera de los dos dejaba de crear en cuanto existía un conector con el
     * mismo número que la estación.
     */
    @Test
    void testAddSecondConnectorToSameStation() {
        when(stationRepository.findById(10L)).thenReturn(Optional.of(mockStation));
        when(connectorRepository.save(any(Connector.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Connector first = terminalService.addConnector(10L, ConnectorType.CCS2, new BigDecimal("50.00"));
        Connector second = terminalService.addConnector(10L, ConnectorType.TYPE_2, new BigDecimal("22.00"));

        assertNotSame(first, second);
        assertEquals(ConnectorType.CCS2, first.getConnectorType());
        assertEquals(ConnectorType.TYPE_2, second.getConnectorType());
        verify(connectorRepository, times(2)).save(any(Connector.class));
    }

    @Test
    void testAddConnectorToMissingStation() {
        when(stationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> terminalService.addConnector(99L, ConnectorType.CCS2, new BigDecimal("50.00")));
        verify(connectorRepository, never()).save(any(Connector.class));
    }

    @Test
    void testConfigureMissingConnector() {
        when(connectorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> terminalService.configureConnector(99L, ConnectorType.CCS2, new BigDecimal("50.00")));
        verify(connectorRepository, never()).save(any(Connector.class));
    }

    @Test
    void testSearchStationsWithinRadius() {
        when(stationRepository.findAllActive()).thenReturn(List.of(mockStation));
        when(connectorRepository.findByStationId(10L)).thenReturn(List.of(mockConnector));

        SearchCriteria criteria = new SearchCriteria(-34.61300, -58.38100, 5.0, null, null, false);
        List<StationResult> results = terminalService.search(criteria);

        assertEquals(1, results.size());
        assertEquals(10L, results.get(0).stationId());
        assertTrue(results.get(0).distanceKm() < 5.0);
    }

    /**
     * Los tres filtros, uno por uno, sobre una estación con un solo conector CCS2 de 50 kW
     * disponible.
     *
     * <p>Cuando el filtro no lo deja pasar, la estación NO tiene que aparecer en los resultados.
     * Antes aparecía con la lista de conectores vacía, y este mismo test afirmaba que así estaba
     * bien: buscar CHADEMO devolvía una estación que no tiene ninguno.
     */
    @Test
    void testSearchCombinedFiltersEdgeCases() {
        when(stationRepository.findAllActive()).thenReturn(List.of(mockStation));
        when(connectorRepository.findByStationId(10L)).thenReturn(List.of(mockConnector));

        // 1. Los tres filtros dan: CCS2, mínimo 40 kW y disponible.
        SearchCriteria matchAll =
                new SearchCriteria(-34.61300, -58.38100, 5.0, ConnectorType.CCS2, new BigDecimal("40.00"), true);
        List<StationResult> all = terminalService.search(matchAll);
        assertEquals(1, all.size());
        assertEquals(1, all.get(0).matchingConnectors().size());

        // 2. Otro tipo de conector.
        SearchCriteria typeMismatch =
                new SearchCriteria(-34.61300, -58.38100, 5.0, ConnectorType.CHADEMO, new BigDecimal("40.00"), true);
        assertTrue(terminalService.search(typeMismatch).isEmpty());

        // 3. Más potencia de la que da el conector.
        SearchCriteria powerMismatch =
                new SearchCriteria(-34.61300, -58.38100, 5.0, ConnectorType.CCS2, new BigDecimal("100.00"), true);
        assertTrue(terminalService.search(powerMismatch).isEmpty());

        // 4. El único conector está fuera de servicio y se pidieron solo los disponibles.
        mockConnector.setOperationalStatus(OperationalStatus.OUT_OF_SERVICE);
        SearchCriteria unavailableMismatch =
                new SearchCriteria(-34.61300, -58.38100, 5.0, ConnectorType.CCS2, new BigDecimal("40.00"), true);
        assertTrue(terminalService.search(unavailableMismatch).isEmpty());
    }

    /**
     * El filtro recorta los conectores, no la estación.
     *
     * <p>Es la otra mitad del arreglo y la que evita pasarse de largo: una estación que tiene un
     * conector que sirve y otro que no tiene que seguir apareciendo, con el que sirve nada más.
     */
    @Test
    void testSearchKeepsTheStationWithOnlyTheConnectorsThatMatch() {
        Connector slowType2 = connector(2L, ConnectorType.TYPE_2, "22.00", OperationalStatus.AVAILABLE);

        when(stationRepository.findAllActive()).thenReturn(List.of(mockStation));
        when(connectorRepository.findByStationId(10L)).thenReturn(List.of(mockConnector, slowType2));

        SearchCriteria onlyCcs2 = new SearchCriteria(-34.61300, -58.38100, 5.0, ConnectorType.CCS2, null, false);
        List<StationResult> results = terminalService.search(onlyCcs2);

        assertEquals(1, results.size());
        assertEquals(1, results.get(0).matchingConnectors().size());
        assertEquals(
                ConnectorType.CCS2, results.get(0).matchingConnectors().get(0).connectorType());
    }

    /**
     * Una estación con todos sus conectores fuera de servicio desaparece al pedir solo los
     * disponibles, aunque tenga varios.
     *
     * <p>Es el caso que el `seed` de datos de ejemplo usa como testigo del filtro.
     */
    @Test
    void testSearchDropsTheStationWithEveryConnectorOutOfService() {
        mockConnector.setOperationalStatus(OperationalStatus.OUT_OF_SERVICE);
        Connector alsoBroken = connector(2L, ConnectorType.TYPE_2, "11.00", OperationalStatus.OUT_OF_SERVICE);

        when(stationRepository.findAllActive()).thenReturn(List.of(mockStation));
        when(connectorRepository.findByStationId(10L)).thenReturn(List.of(mockConnector, alsoBroken));

        SearchCriteria onlyAvailable = new SearchCriteria(-34.61300, -58.38100, 5.0, null, null, true);
        assertTrue(terminalService.search(onlyAvailable).isEmpty());

        // Sin ese filtro, la misma estación sí es un resultado: se puede reservar para más tarde.
        SearchCriteria noFilter = new SearchCriteria(-34.61300, -58.38100, 5.0, null, null, false);
        assertEquals(1, terminalService.search(noFilter).size());
    }

    /**
     * Una estación recién dada de alta, todavía sin conectores, no es un lugar donde cargar.
     *
     * <p>No hay ningún filtro puesto acá: es la estación la que no tiene con qué.
     */
    @Test
    void testSearchDropsTheStationWithoutConnectors() {
        when(stationRepository.findAllActive()).thenReturn(List.of(mockStation));
        when(connectorRepository.findByStationId(10L)).thenReturn(List.of());

        SearchCriteria noFilter = new SearchCriteria(-34.61300, -58.38100, 5.0, null, null, false);

        assertTrue(terminalService.search(noFilter).isEmpty());
    }

    @Test
    void testOwnershipChecksForStationsAndConnectors() {
        mockStation.setOwnerId(7L);
        when(stationRepository.findById(10L)).thenReturn(Optional.of(mockStation));
        when(stationRepository.findById(99L)).thenReturn(Optional.empty());
        when(connectorRepository.findById(1L)).thenReturn(Optional.of(mockConnector));
        when(connectorRepository.findById(99L)).thenReturn(Optional.empty());

        assertTrue(terminalService.isStationOwner(10L, 7L));
        assertFalse(terminalService.isStationOwner(10L, 8L));
        assertFalse(terminalService.isStationOwner(99L, 7L));

        assertTrue(terminalService.isConnectorOwner(1L, 7L));
        assertFalse(terminalService.isConnectorOwner(1L, 8L));
        assertFalse(terminalService.isConnectorOwner(99L, 7L));
    }

    @Test
    void testUpdateStationReplacesFieldsAndPreservesPhotosWhenNull() {
        List<String> initialPhotos = List.of("https://cdn.ecopedia.test/old.jpg");
        mockStation.setPhotoUrls(initialPhotos);
        mockStation.setNodeId("NODO-A");
        when(stationRepository.findById(10L)).thenReturn(Optional.of(mockStation));
        when(stationRepository.save(any(Station.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<String> newPhotos = List.of("https://cdn.ecopedia.test/new.jpg");
        StationData updateWithPhotos =
                new StationData("Nuevo Nombre", "Nueva Dir 123", -34.62, -58.39, newPhotos, "NODO-B");
        Station updated = terminalService.updateStation(10L, updateWithPhotos);

        assertEquals("Nuevo Nombre", updated.getName());
        assertEquals("Nueva Dir 123", updated.getAddress());
        assertEquals(-34.62, updated.getLatitude());
        assertEquals(-58.39, updated.getLongitude());
        assertEquals(newPhotos, updated.getPhotoUrls());
        assertEquals("NODO-B", updated.getNodeId());

        StationData updateWithoutPhotos = new StationData("Otro Nombre", "Otra Dir 456", -34.63, -58.40, null, null);
        Station preservedPhotos = terminalService.updateStation(10L, updateWithoutPhotos);

        assertEquals(newPhotos, preservedPhotos.getPhotoUrls());
        assertNull(preservedPhotos.getNodeId());
    }

    @Test
    void testUpdateAndDeactivateMissingStationThrowNotFound() {
        when(stationRepository.findById(99L)).thenReturn(Optional.empty());
        StationData data = new StationData("X", "Y", -34.6, -58.3, List.of(), null);

        assertThrows(ResourceNotFoundException.class, () -> terminalService.updateStation(99L, data));
        assertThrows(ResourceNotFoundException.class, () -> terminalService.deactivateStation(99L));
        assertThrows(ResourceNotFoundException.class, () -> terminalService.getStation(99L));
        verify(stationRepository, never()).save(any());
    }

    @Test
    void testChangeOperationalStatusAndConnectorQueriesAndRemoval() {
        when(connectorRepository.findById(1L)).thenReturn(Optional.of(mockConnector));
        when(connectorRepository.findById(99L)).thenReturn(Optional.empty());
        when(connectorRepository.save(any(Connector.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectorRepository.findAll()).thenReturn(List.of(mockConnector));
        when(connectorRepository.findByStationId(10L)).thenReturn(List.of(mockConnector));
        when(stationRepository.findById(10L)).thenReturn(Optional.of(mockStation));
        when(stationRepository.findAllActive()).thenReturn(List.of(mockStation));

        terminalService.changeOperationalStatus(1L, OperationalStatus.OUT_OF_SERVICE);
        assertEquals(OperationalStatus.OUT_OF_SERVICE, mockConnector.getOperationalStatus());
        assertThrows(
                ResourceNotFoundException.class,
                () -> terminalService.changeOperationalStatus(99L, OperationalStatus.AVAILABLE));

        assertSame(mockConnector, terminalService.getConnector(1L));
        assertThrows(ResourceNotFoundException.class, () -> terminalService.getConnector(99L));
        assertEquals(List.of(mockConnector), terminalService.getAllConnectors());
        assertEquals(List.of(mockConnector), terminalService.getConnectorsByStation(10L));
        assertSame(mockStation, terminalService.getStation(10L));
        assertEquals(List.of(mockStation), terminalService.getAllStations());

        terminalService.removeConnector(1L);
        verify(connectorRepository).delete(mockConnector);
        assertThrows(ResourceNotFoundException.class, () -> terminalService.removeConnector(99L));
    }

    private Connector connector(Long id, ConnectorType type, String maxPowerKw, OperationalStatus status) {
        Connector connector = new Connector();
        connector.setId(id);
        connector.setStation(mockStation);
        connector.setConnectorType(type);
        connector.setMaxPowerKw(new BigDecimal(maxPowerKw));
        connector.setOperationalStatus(status);
        return connector;
    }
}
