package com.ecopedia.core.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.terminal.data.JpaConnectorRepository;
import com.ecopedia.core.terminal.data.JpaStationRepository;
import com.ecopedia.core.terminal.domain.ConnectorType;
import com.ecopedia.core.terminal.domain.SearchCriteria;
import com.ecopedia.core.terminal.domain.Station;
import com.ecopedia.core.terminal.domain.StationData;
import com.ecopedia.core.terminal.domain.StationResult;
import com.ecopedia.core.terminal.domain.TerminalService;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * La búsqueda y el listado de estaciones contra la base (ECO-14).
 *
 * <p><b>Dos cosas que un test con mocks no puede ver:</b> que la consulta del recuadro devuelva lo
 * mismo que medir la distancia a todas las estaciones, y cuántas consultas hace cada pedido. Antes
 * la búsqueda traía todas las activas y pedía los conectores de a una estación; ahora son dos
 * consultas, haya las estaciones que haya.
 *
 * <p>Las estaciones las crea el propio test, en una grilla alrededor del Obelisco, y no se usa el
 * seed: los tests de Terminales comparten la base en memoria y varios la vacían al terminar.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class StationQueriesTest {

    private static final double OBELISCO_LAT = -34.6037;
    private static final double OBELISCO_LON = -58.3816;

    /** La grilla: 5 × 5 estaciones, de a 0,05° (unos 5 km), más una dada de baja en el centro. */
    private static final int GRID = 5;

    private static final double STEP = 0.05;

    @Autowired
    private TerminalService terminalService;

    @Autowired
    private JpaStationRepository stationRepository;

    @Autowired
    private JpaConnectorRepository connectorRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void givenAGridOfStations() {
        connectorRepository.deleteAll();
        stationRepository.deleteAll();
        for (int row = 0; row < GRID; row++) {
            for (int column = 0; column < GRID; column++) {
                double latitude = OBELISCO_LAT + (row - GRID / 2) * STEP;
                double longitude = OBELISCO_LON + (column - GRID / 2) * STEP;
                Station station = terminalService.createStation(
                        1L,
                        new StationData(
                                "Grilla " + row + "-" + column,
                                "Calle " + row + column,
                                latitude,
                                longitude,
                                List.of("https://fotos.ecopedia.test/" + row + column + ".jpg"),
                                null));
                terminalService.addConnector(station.getId(), ConnectorType.CCS2, new BigDecimal("50.00"));
                terminalService.addConnector(station.getId(), ConnectorType.TYPE_2, new BigDecimal("22.00"));
            }
        }
        Station retired = terminalService.createStation(
                1L, new StationData("Dada de baja", "Centro", OBELISCO_LAT, OBELISCO_LON, List.of(), null));
        terminalService.addConnector(retired.getId(), ConnectorType.CCS2, new BigDecimal("50.00"));
        terminalService.deactivateStation(retired.getId());

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @AfterEach
    void cleanUp() {
        connectorRepository.deleteAll();
        stationRepository.deleteAll();
    }

    /**
     * El recuadro es solo un atajo: el resultado tiene que ser exactamente el de medir la distancia
     * a todas las activas, también en los radios que dejan estaciones en las esquinas del recuadro.
     */
    @ParameterizedTest(name = "radio de {0} km")
    @CsvSource({"0.5", "5", "6", "8", "12", "15", "100"})
    @DisplayName("La búsqueda con el recuadro devuelve las mismas estaciones que medir todas")
    void theBoxFindsTheSameStationsAsMeasuringAll(double radiusKm) {
        List<Long> expected = new ArrayList<>();
        for (Station station : stationRepository.findAll()) {
            if (station.isActive() && distanceKm(station) <= radiusKm) expected.add(station.getId());
        }

        List<Long> found =
                terminalService
                        .search(new SearchCriteria(OBELISCO_LAT, OBELISCO_LON, radiusKm, null, null, false))
                        .stream()
                        .map(StationResult::stationId)
                        .toList();

        assertThat(found).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("Buscar en toda la grilla hace dos consultas, no una por estación")
    void searchRunsTwoQueries() {
        List<StationResult> results =
                terminalService.search(new SearchCriteria(OBELISCO_LAT, OBELISCO_LON, 100, null, null, false));

        assertThat(results).hasSize(GRID * GRID);
        assertThat(results)
                .allSatisfy(result -> assertThat(result.matchingConnectors()).hasSize(2));
        assertThat(statistics.getPrepareStatementCount())
                .as("las estaciones con sus fotos, y los conectores de todas")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("El listado de estaciones con sus conectores hace dos consultas, no una por estación")
    void listingRunsTwoQueries() throws Exception {
        mockMvc.perform(get("/api/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(GRID * GRID))
                .andExpect(jsonPath("$[0].connectors.length()").value(2))
                .andExpect(jsonPath("$[0].photoUrls.length()").value(1));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    /** La misma fórmula que el servicio: lo que se compara es qué estaciones entran, no la cuenta. */
    private static double distanceKm(Station station) {
        double dLat = Math.toRadians(station.getLatitude() - OBELISCO_LAT);
        double dLon = Math.toRadians(station.getLongitude() - OBELISCO_LON);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(OBELISCO_LAT))
                        * Math.cos(Math.toRadians(station.getLatitude()))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
