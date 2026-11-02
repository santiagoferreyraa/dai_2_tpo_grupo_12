package com.ecopedia.core.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Las dos cuentas con las que se entra a un sistema recién levantado: un {@code ADMIN} y un
 * {@code CPO}. Salen del .env ({@code ECOPEDIA_ADMIN_EMAIL}, {@code ECOPEDIA_ADMIN_PASSWORD},
 * {@code ECOPEDIA_CPO_EMAIL} y {@code ECOPEDIA_CPO_PASSWORD}).
 *
 * <p>Existen por el criterio G1 de la prueba del TP: quien no conoce el proyecto tiene que poder
 * levantarlo y entrar sin tocar la base a mano. Desde el {@code ADMIN} se promueve al resto en
 * {@code /admin}.
 *
 * <p><b>Las contraseñas no están en el repo</b> (G6): las pone quien levanta el sistema en su .env,
 * o las genera {@code pnpm env:init}.
 */
@ConfigurationProperties(prefix = "ecopedia.initial-accounts")
public record InitialAccounts(Account admin, Account cpo) {

    public InitialAccounts {
        admin = admin == null ? Account.NONE : admin;
        cpo = cpo == null ? Account.NONE : cpo;
    }

    /**
     * Una cuenta. Sin mail o sin contraseña, no se crea: es lo normal en {@code pnpm dev} y en los
     * tests, que no traen .env.
     */
    public record Account(String email, String password) {

        static final Account NONE = new Account(null, null);

        boolean configured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
