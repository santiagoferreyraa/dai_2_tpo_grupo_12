package com.ecopedia.core.bootstrap;

import com.ecopedia.core.terminal.domain.Station;
import com.ecopedia.core.terminal.domain.StationRepository;
import com.ecopedia.core.user.domain.Role;
import com.ecopedia.core.user.domain.User;
import com.ecopedia.core.user.domain.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea las cuentas iniciales y, la primera vez que se crea la del {@code CPO}, reparte las
 * estaciones sembradas entre los operadores.
 *
 * <p><b>Nunca pisa nada.</b> Si la cuenta ya existe, no se toca, tampoco la contraseña: quien
 * entra la cambia desde su perfil, y en el arranque siguiente seguiría valiendo la suya.
 *
 * <p><b>Nunca escribe una contraseña ni un mail en el log</b> (eje D de la prueba del TP): las
 * líneas dicen el rol y el id.
 */
@Service
public class InitialSetup {

    private static final Logger log = LoggerFactory.getLogger(InitialSetup.class);

    /** La marca en {@code one_time_tasks}: el reparto pasa una sola vez por base. */
    static final String SPLIT_SEED_STATIONS = "split-seed-stations";

    /** Las 15 de V202609071900__seed_stations.sql, sembradas todas con {@code owner_id} 1. */
    static final List<Long> SEED_STATION_IDS =
            LongStream.rangeClosed(1, 15).boxed().toList();

    /** El mismo mínimo que el registro (RegisterRequest). */
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository userRepository;
    private final StationRepository stationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public InitialSetup(
            UserRepository userRepository,
            StationRepository stationRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.stationRepository = stationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Transactional
    public void apply(InitialAccounts accounts) {
        createIfMissing(accounts.admin(), Role.ADMIN, "Administrador de Ecopedia", "ECOPEDIA_ADMIN");
        boolean cpoCreated = createIfMissing(accounts.cpo(), Role.CPO, "Operador de Ecopedia", "ECOPEDIA_CPO");
        if (cpoCreated) {
            splitSeedStationsOnce();
        }
    }

    private boolean createIfMissing(InitialAccounts.Account account, Role role, String fullName, String variable) {
        if (!account.configured()) {
            log.info(
                    "Cuenta inicial {} sin configurar ({}_EMAIL y {}_PASSWORD en el .env): no se crea",
                    role,
                    variable,
                    variable);
            return false;
        }
        /*
         * Se corta el arranque en vez de crear una cuenta con la que después no se puede cambiar la
         * contraseña (el formulario pide 6). El mensaje nombra la variable, nunca el valor.
         */
        if (account.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    variable + "_PASSWORD tiene que tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }

        // Sin pasar a minúsculas: el registro y el login tampoco lo hacen.
        String email = account.email().trim();
        if (userRepository.findByEmail(email).isPresent()) {
            log.info("La cuenta inicial {} ya existe: no se toca", role);
            return false;
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(account.password()));
        user.setFullName(fullName);
        user.setRole(role);
        user.setActive(true);
        User saved = userRepository.save(user);
        log.info("Cuenta inicial {} creada (usuario {})", role, saved.getId());
        return true;
    }

    /*
     * Por turno, en orden de id, entre los CPO activos (el recién creado incluido): con tres CPO,
     * cinco estaciones cada uno. En un sistema recién levantado el único CPO es el inicial, y se
     * queda con las quince. Solo las sembradas: una estación que un CPO dio de alta es suya.
     *
     * Pasa una sola vez por base (Manuel, 02/11), no cada vez que se crea un CPO: la marca queda en
     * one_time_tasks, y aunque el .env cambie de mail y se cree otra cuenta inicial, no se repite.
     */
    private void splitSeedStationsOnce() {
        Integer done = jdbc.queryForObject(
                "select count(*) from core.one_time_tasks where name = ?", Integer.class, SPLIT_SEED_STATIONS);
        if (done != null && done > 0) {
            return;
        }

        List<User> operators = userRepository.findByRole(Role.CPO).stream()
                .filter(User::isActive)
                .sorted(Comparator.comparing(User::getId))
                .toList();
        List<Station> seeded = SEED_STATION_IDS.stream()
                .map(stationRepository::findById)
                .flatMap(Optional::stream)
                .toList();

        for (int i = 0; i < seeded.size(); i++) {
            Station station = seeded.get(i);
            station.setOwnerId(operators.get(i % operators.size()).getId());
            stationRepository.save(station);
        }

        jdbc.update("insert into core.one_time_tasks (name) values (?)", SPLIT_SEED_STATIONS);
        log.info("Las {} estaciones sembradas quedaron repartidas entre {} CPO", seeded.size(), operators.size());
    }
}
