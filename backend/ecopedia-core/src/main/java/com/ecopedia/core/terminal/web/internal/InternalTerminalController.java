package com.ecopedia.core.terminal.web.internal;

import com.ecopedia.core.terminal.domain.TerminalService;
import com.ecopedia.core.terminal.web.dto.ConnectorResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las operaciones de Terminales que consume SesionesDeCarga y no un usuario (ECO-44).
 *
 * <p>Cuelgan de {@code /internal/**}, así que solo pasan con la clave de servicio: ver
 * {@link com.ecopedia.core.security.InternalTokenFilter}. Un conductor no puede preguntar qué conector
 * tiene tal código; tiene que pararse frente a él y escanearlo, que es lo que acredita RF11.
 */
@RestController
@RequestMapping("/internal/terminals")
public class InternalTerminalController {

    private final TerminalService terminalService;

    public InternalTerminalController(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    /**
     * El conector de un código de acceso. 404 si el código no es válido o no es de nadie (lo traduce
     * {@code ApiExceptionHandler}).
     */
    @PostMapping("/connectors/resolve-code")
    public ConnectorResponse resolveAccessCode(@Valid @RequestBody ResolveAccessCodeRequest request) {
        return ConnectorResponse.fromDomain(terminalService.getConnectorByAccessCode(request.code()));
    }
}
