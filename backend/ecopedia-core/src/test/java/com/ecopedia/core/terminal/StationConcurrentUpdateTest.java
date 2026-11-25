package com.ecopedia.core.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecopedia.core.shared.StaleVersionException;
import com.ecopedia.core.terminal.data.JpaConnectorRepository;
import com.ecopedia.core.terminal.data.JpaStationRepository;
import com.ecopedia.core.terminal.domain.Station;
import com.ecopedia.core.terminal.domain.StationData;
import com.ecopedia.core.terminal.domain.TerminalService;
import com.ecopedia.core.web.EntityTags;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Dos operadores guardan la misma estación a la vez, los dos con el ETag que leyeron (QA-30): gana
 * uno y el resto recibe 412, aunque hayan pasado la comprobación al mismo tiempo. Eso último lo
 * frena {@code @Version} en el commit, y {@link EntityTags#write} lo traduce al mismo 412.
 *
 * <p>Por el servicio y no por HTTP: lo que se prueba es la transacción contra la base, y MockMvc no
 * promete atender pedidos en paralelo.
 */
@SpringBootTest
@ActiveProfiles("dev")
class StationConcurrentUpdateTest {

    private static final int WRITERS = 8;

    @Autowired
    private TerminalService terminalService;

    @Autowired
    private JpaStationRepository stationRepository;

    @Autowired
    private JpaConnectorRepository connectorRepository;

    @AfterEach
    void cleanUp() {
        connectorRepository.deleteAll();
        stationRepository.deleteAll();
    }

    private static StationData data(String name) {
        return new StationData(name, "Av. San Juan 2901", -34.603754, -58.381659, new ArrayList<>(), null);
    }

    @Test
    @DisplayName("Con el mismo ETag, de ocho escrituras simultáneas gana una y las otras siete dan 412")
    void onlyOneWriterWins() throws Exception {
        Station station = terminalService.createStation(1L, data("Original"));
        String tagReadByAll =
                EntityTags.of(terminalService.getStation(station.getId()).getVersion());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(WRITERS);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < WRITERS; i++) {
                String name = "Escritor " + i;
                results.add(pool.submit(() -> {
                    start.await();
                    var condition = EntityTags.ifMatch(tagReadByAll);
                    try {
                        EntityTags.write(
                                condition, () -> terminalService.updateStation(station.getId(), data(name), condition));
                        return true;
                    } catch (StaleVersionException rejected) {
                        return false;
                    }
                }));
            }
            start.countDown();

            int saved = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) saved++;
            }
            assertThat(saved).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        Station after = terminalService.getStation(station.getId());
        assertThat(after.getName()).startsWith("Escritor ");
        assertThat(after.getVersion()).isEqualTo(station.getVersion() + 1);
    }
}
