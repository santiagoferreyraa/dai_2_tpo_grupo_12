package com.ecopedia.core.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Corre {@link InitialSetup} al terminar de arrancar core, cuando Flyway ya armó la base.
 *
 * <p>Corre en todos los perfiles: sin las variables en el entorno (los tests, un {@code pnpm dev}
 * sin .env) no crea nada y deja una línea en el log.
 */
@Configuration
@EnableConfigurationProperties(InitialAccounts.class)
public class InitialSetupRunner implements ApplicationRunner {

    private final InitialSetup initialSetup;
    private final InitialAccounts accounts;

    public InitialSetupRunner(InitialSetup initialSetup, InitialAccounts accounts) {
        this.initialSetup = initialSetup;
        this.accounts = accounts;
    }

    @Override
    public void run(ApplicationArguments args) {
        initialSetup.apply(accounts);
    }
}
