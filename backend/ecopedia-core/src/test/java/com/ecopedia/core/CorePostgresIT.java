package com.ecopedia.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecopedia.core.terminal.domain.Station;
import com.ecopedia.core.terminal.domain.StationRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core contra PostgreSQL de verdad (QA-24): las migraciones, la siembra y las consultas propias,
 * con el motor que se usa en producción y no con H2.
 */
class CorePostgresIT extends ContainersSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StationRepository stations;

    @Test
    @DisplayName("Flyway aplica todas las migraciones de core en PostgreSQL, en su schema y sin fallas")
    void migrationsRunCleanOnPostgres() {
        Integer failed = jdbc.queryForObject(
                "select count(*) from core.flyway_schema_history where success = false", Integer.class);
        Integer applied =
                jdbc.queryForObject("select count(*) from core.flyway_schema_history where success", Integer.class);

        assertThat(failed).isZero();
        assertThat(applied).isPositive();
        assertThat(jdbc.queryForObject("select current_setting('server_version_num')::int", Integer.class))
                .as("el motor es PostgreSQL 16, el mismo que el compose")
                .isBetween(160000, 169999);
    }

    @Test
    @DisplayName("La siembra deja las 15 estaciones, todas activas")
    void seedLeavesTheFifteenStations() {
        assertThat(stations.findAll()).hasSize(15);
        assertThat(stations.findAllActive()).hasSize(15);
    }

    @Test
    @Transactional
    @DisplayName("Una estación dada de baja no sale entre las activas")
    void inactiveStationIsLeftOut() {
        Station station = stations.findById(3L).orElseThrow();
        station.setActive(false);
        stations.save(station);

        List<Station> active = stations.findAllActive();

        assertThat(active).hasSize(14).extracting(Station::getId).doesNotContain(3L);
    }

    @Test
    @Transactional
    @DisplayName("PostgreSQL rechaza dos cuentas con el mismo mail")
    void emailIsUnique() {
        String insert =
                "insert into core.users (email, password_hash, full_name, role, active) values (?, ?, ?, 'CONDUCTOR', true)";
        jdbc.update(insert, "repetido@ecopedia.test", "hash", "Primera");

        assertThatThrownBy(() -> jdbc.update(insert, "repetido@ecopedia.test", "hash", "Segunda"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
