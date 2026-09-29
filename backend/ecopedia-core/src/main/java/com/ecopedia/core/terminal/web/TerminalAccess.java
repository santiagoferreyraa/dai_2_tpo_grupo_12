package com.ecopedia.core.terminal.web;

import com.ecopedia.core.terminal.domain.TerminalService;
import com.ecopedia.core.user.domain.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Quién es dueño de qué, para las reglas de {@code @PreAuthorize} del ABM y de la tarifa.
 *
 * <p><b>Por qué existe.</b> Con el rol solo, cualquier {@code CPO} editaba, apagaba o borraba la
 * estación de otro operador, y le ponía precio a sus conectores: la regla decía "ser operador" y
 * no "ser el operador de esto". Es el error de autorización más común en una API (el primero
 * del OWASP API Top 10), y el que un testeo de seguridad prueba primero.
 *
 * <p><b>Se usa desde la anotación, no desde el cuerpo del método.</b> Las reglas quedan
 * escritas en un solo lugar y a la vista, igual que las de rol:
 *
 * <pre>{@code
 * @PreAuthorize("hasRole('ADMIN') or (hasRole('CPO') and @terminalAccess.ownsStation(#id, authentication))")
 * }</pre>
 *
 * <p>El usuario sale del token: {@code JwtAuthenticationFilter} deja el email como nombre de la
 * autenticación, igual que en {@code UserController}. Un email que ya no está en la base no es
 * dueño de nada.
 */
@Component("terminalAccess")
public class TerminalAccess {

    private final TerminalService terminalService;
    private final UserService userService;

    public TerminalAccess(TerminalService terminalService, UserService userService) {
        this.terminalService = terminalService;
        this.userService = userService;
    }

    /** Si quien llama es el dueño de la estación. */
    public boolean ownsStation(Long stationId, Authentication authentication) {
        Long userId = userIdOf(authentication);
        return userId != null && terminalService.isStationOwner(stationId, userId);
    }

    /** Si quien llama es el dueño de la estación de la que cuelga el conector. */
    public boolean ownsConnector(Long connectorId, Authentication authentication) {
        Long userId = userIdOf(authentication);
        return userId != null && connectorId != null && terminalService.isConnectorOwner(connectorId, userId);
    }

    /** El id del usuario autenticado, o {@code null} si no hay sesión o ya no está en la base. */
    public Long userIdOf(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            return userService.getProfileByEmail(authentication.getName()).getId();
        } catch (IllegalArgumentException missing) {
            return null;
        }
    }
}
