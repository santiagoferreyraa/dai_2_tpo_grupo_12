package com.ecopedia.core.terminal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecopedia.core.terminal.data.JpaConnectorRepository;
import com.ecopedia.core.terminal.data.JpaStationRepository;
import com.ecopedia.core.terminal.domain.Connector;
import com.ecopedia.core.terminal.domain.ConnectorType;
import com.ecopedia.core.terminal.domain.Station;
import com.ecopedia.core.terminal.domain.StationData;
import com.ecopedia.core.terminal.domain.TerminalService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de los endpoints de lectura de estaciones contra la aplicación levantada.
 *
 * <p><b>Por qué existe esta clase.</b> {@code TerminalServiceTest} le pone dobles a los
 * repositorios, así que nunca hay una base ni una sesión de Hibernate y los errores de carga
 * perezosa le pasan por al lado. Esta clase levanta el contexto entero —Flyway, JPA y la capa
 * web— y pide los recursos como los pide el navegador. Es la única forma de que
 * {@code mvn verify} vea una clase de error que hasta ahora aparecía recién al arrancar la
 * aplicación a mano.
 *
 * <p><b>No lleva {@code @Transactional}, y es a propósito.</b> Anotar la clase haría que cada
 * prueba corriera adentro de una transacción abierta, o sea con una sesión de Hibernate viva
 * durante la petición. Eso inicializaría las colecciones perezosas de casualidad y estas
 * pruebas pasarían aunque el error estuviera presente: justamente lo que vinieron a detectar.
 * Por eso los datos se limpian a mano en {@link #cleanUp()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TerminalApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TerminalService terminalService;

    @Autowired
    private JpaStationRepository stationRepository;

    @Autowired
    private JpaConnectorRepository connectorRepository;

    /*
     * El perfil 'dev' usa H2 con DB_CLOSE_DELAY=-1, así que la base sobrevive a cada prueba
     * dentro de la misma JVM. Sin esta limpieza, una prueba vería las estaciones de la anterior.
     * Los conectores van primero: son los que apuntan a la estación.
     */
    @AfterEach
    void cleanUp() {
        connectorRepository.deleteAll();
        stationRepository.deleteAll();
    }

    private Station givenStation(String name, List<String> photoUrls) {
        return terminalService.createStation(
                1L, new StationData(name, "Av. San Juan 2901", -34.603754, -58.381659, photoUrls));
    }

    @Test
    @DisplayName("El listado devuelve las estaciones con sus fotos")
    void listsStationsWithTheirPhotos() throws Exception {
        givenStation("YPF Constitución", List.of("https://example.com/a.jpg"));

        mockMvc.perform(get("/api/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("YPF Constitución"))
                .andExpect(jsonPath("$[0].photoUrls[0]").value("https://example.com/a.jpg"));
    }

    /*
     * Una estación sin fotos no es un caso de borde: la colección vacía también es perezosa y
     * rompe igual que una con datos. Sin esta prueba, el error se escaparía en la mitad de los
     * casos.
     */
    @Test
    @DisplayName("El listado devuelve también las estaciones sin fotos")
    void listsStationsWithoutPhotos() throws Exception {
        givenStation("Estación sin fotos", List.of());

        mockMvc.perform(get("/api/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].photoUrls").isEmpty());
    }

    @Test
    @DisplayName("La consulta por id devuelve la estación con sus fotos")
    void readsOneStationWithItsPhotos() throws Exception {
        Station station = givenStation("YPF Constitución", List.of("https://example.com/a.jpg"));

        mockMvc.perform(get("/api/stations/{id}", station.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrls[0]").value("https://example.com/a.jpg"));
    }

    /*
     * La potencia la validaba solo el formulario del ABM, así que por API se podía dejar un
     * conector en 0 kW o en negativo. Ningún filtro de potencia de la búsqueda lo devuelve
     * nunca, o sea que la estación queda con un conector que el conductor no ve jamás.
     */
    @Test
    @DisplayName("El alta de un conector rechaza la potencia en cero")
    void rejectsZeroPowerOnCreate() throws Exception {
        Station station = givenStation("YPF Constitución", List.of());

        mockMvc.perform(post("/api/stations/{id}/connectors", station.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorType\":\"CCS2\",\"maxPowerKw\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("El alta de un conector rechaza la potencia negativa")
    void rejectsNegativePowerOnCreate() throws Exception {
        Station station = givenStation("YPF Constitución", List.of());

        mockMvc.perform(post("/api/stations/{id}/connectors", station.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorType\":\"CCS2\",\"maxPowerKw\":-50}"))
                .andExpect(status().isBadRequest());
    }

    /* El mismo DTO viaja en la reconfiguración: si solo se cubriera el alta, se entra por acá. */
    @Test
    @DisplayName("La reconfiguración de un conector rechaza la potencia en cero")
    void rejectsZeroPowerOnConfigure() throws Exception {
        Station station = givenStation("YPF Constitución", List.of());
        Connector connector = terminalService.addConnector(station.getId(), ConnectorType.CCS2, new BigDecimal("50"));

        mockMvc.perform(post("/api/connectors/{id}/configure", connector.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectorType\":\"CCS2\",\"maxPowerKw\":0}"))
                .andExpect(status().isBadRequest());
    }

    /* Un radio en cero o negativo no describe ninguna superficie: no hay búsqueda que hacer. */
    @Test
    @DisplayName("La búsqueda rechaza un radio que no es positivo")
    void rejectsNonPositiveSearchRadius() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("lat", "-34.6")
                        .param("lon", "-58.38")
                        .param("radiusKm", "-1"))
                .andExpect(status().isBadRequest());
    }
}
