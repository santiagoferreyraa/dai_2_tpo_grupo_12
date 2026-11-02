package com.ecopedia.core.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecopedia.core.bootstrap.InitialAccounts.Account;
import com.ecopedia.core.user.data.JpaUserRepository;
import com.ecopedia.core.user.domain.Credentials;
import com.ecopedia.core.user.domain.RegistrationData;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Las cuentas iniciales del .env y el reparto de las estaciones sembradas, contra la base de
 * verdad (H2 con las migraciones de Flyway, que siembran las 15 estaciones con dueño 1).
 */
@SpringBootTest
@ActiveProfiles("dev")
class InitialSetupTest {

    private static final String PASSWORD = "11223344";
    private static final String ADMIN = "admin-inicial@ecopedia.test";
    private static final String CPO = "cpo-inicial@ecopedia.test";

    @Autowired
    private InitialSetup initialSetup;

    @Autowired
    private UserService userService;

    @Autowired
    private JpaUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    /* Otros tests del mismo contexto cuentan con las sembradas del usuario 1. */
    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
        jdbc.update("delete from core.one_time_tasks");
        jdbc.update("update core.stations set owner_id = 1 where id <= 15");
        jdbc.update("delete from core.stations where name = 'Alta propia'");
    }

    private static InitialAccounts accounts(String adminEmail, String cpoEmail) {
        return new InitialAccounts(new Account(adminEmail, PASSWORD), new Account(cpoEmail, PASSWORD));
    }

    private User givenCpo(String email) {
        return userService.register(new RegistrationData(email, "otraClave1", "CPO existente", Role.CPO));
    }

    private Map<Long, Long> seededPerOwner() {
        return jdbc.queryForList("select owner_id from core.stations where id <= 15", Long.class).stream()
                .collect(Collectors.groupingBy(owner -> owner, Collectors.counting()));
    }

    @Test
    @DisplayName("Crea el ADMIN y el CPO, y se entra con la contraseña del .env")
    void createsBothAccounts() {
        initialSetup.apply(accounts(ADMIN, CPO));

        assertThat(userService.authenticate(new Credentials(ADMIN, PASSWORD)).role())
                .isEqualTo(Role.ADMIN);
        assertThat(userService.authenticate(new Credentials(CPO, PASSWORD)).role())
                .isEqualTo(Role.CPO);
        assertThat(userRepository.findByEmail(ADMIN).orElseThrow().getPasswordHash())
                .isNotEqualTo(PASSWORD);
    }

    @Test
    @DisplayName("Una cuenta que ya existe no se toca: vale la contraseña que le cambió su dueño")
    void neverOverwritesAnExistingAccount() {
        initialSetup.apply(accounts(ADMIN, CPO));
        User admin = userRepository.findByEmail(ADMIN).orElseThrow();
        userService.changePassword(admin.getId(), PASSWORD, "laNuevaDelProfe");

        initialSetup.apply(accounts(ADMIN, CPO));

        assertThat(userService
                        .authenticate(new Credentials(ADMIN, "laNuevaDelProfe"))
                        .userId())
                .isEqualTo(admin.getId());
        assertThat(userRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("Sin mail o sin contraseña no crea nada (pnpm dev, los tests)")
    void skipsWhatIsNotConfigured() {
        initialSetup.apply(new InitialAccounts(new Account("", ""), null));

        assertThat(userRepository.findAll()).isEmpty();
        assertThat(seededPerOwner()).containsOnly(Map.entry(1L, 15L));
    }

    @Test
    @DisplayName("Una contraseña de menos de 6 corta el arranque, sin decir cuál era")
    void rejectsAShortPassword() {
        InitialAccounts accounts = new InitialAccounts(new Account(ADMIN, "12345"), null);

        assertThatThrownBy(() -> initialSetup.apply(accounts))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ECOPEDIA_ADMIN_PASSWORD")
                .hasMessageNotContaining("12345");
    }

    @Test
    @DisplayName("En un sistema nuevo, el CPO inicial se queda con las 15 sembradas")
    void theOnlyCpoGetsAllTheSeededStations() {
        initialSetup.apply(accounts(ADMIN, CPO));

        Long cpo = userRepository.findByEmail(CPO).orElseThrow().getId();
        assertThat(seededPerOwner()).containsOnly(Map.entry(cpo, 15L));
    }

    @Test
    @DisplayName("Con dos CPO ya registrados, las 15 quedan 5, 5 y 5, y un alta propia no se toca")
    void splitsEvenlyAmongTheExistingOperators() {
        Long first = givenCpo("cpo-uno@ecopedia.test").getId();
        Long second = givenCpo("cpo-dos@ecopedia.test").getId();
        jdbc.update(
                "insert into core.stations (name, address, latitude, longitude, owner_id, active)"
                        + " values ('Alta propia', 'Calle 1', -34.6, -58.4, ?, true)",
                second);

        initialSetup.apply(accounts(ADMIN, CPO));

        Long initial = userRepository.findByEmail(CPO).orElseThrow().getId();
        assertThat(seededPerOwner()).containsOnly(Map.entry(first, 5L), Map.entry(second, 5L), Map.entry(initial, 5L));
        assertThat(jdbc.queryForObject("select owner_id from core.stations where name = 'Alta propia'", Long.class))
                .isEqualTo(second);
    }

    @Test
    @DisplayName("El reparto pasa una sola vez: otra cuenta CPO inicial después no lo repite")
    void splitsOnlyOnce() {
        initialSetup.apply(accounts(ADMIN, CPO));
        Map<Long, Long> afterFirst = seededPerOwner();

        initialSetup.apply(accounts(ADMIN, "otro-cpo-inicial@ecopedia.test"));

        assertThat(userRepository.findByEmail("otro-cpo-inicial@ecopedia.test")).isPresent();
        assertThat(seededPerOwner()).isEqualTo(afterFirst);
        assertThat(jdbc.queryForList("select name from core.one_time_tasks", String.class))
                .isEqualTo(List.of(InitialSetup.SPLIT_SEED_STATIONS));
    }
}
