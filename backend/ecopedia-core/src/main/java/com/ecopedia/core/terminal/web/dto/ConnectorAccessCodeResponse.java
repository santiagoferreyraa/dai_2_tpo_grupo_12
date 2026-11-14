package com.ecopedia.core.terminal.web.dto;

import com.ecopedia.core.terminal.domain.Connector;
import com.ecopedia.core.terminal.domain.ConnectorAccessCode;

/**
 * El código de acceso de un conector, para que el operador lo imprima en la terminal (ECO-44).
 *
 * <p>Va aparte de {@link ConnectorResponse} a propósito: esa es pública —la lee el mapa sin sesión—
 * y el código es justamente lo que no tiene que ver nadie que no esté frente a la terminal.
 *
 * @param qrPayload lo que tiene que decir el QR impreso; el PIN, con un prefijo que lo distingue de
 *     cualquier otro QR
 */
public record ConnectorAccessCodeResponse(Long connectorId, String accessCode, String qrPayload) {

    public static ConnectorAccessCodeResponse fromDomain(Connector connector) {
        return new ConnectorAccessCodeResponse(
                connector.getId(), connector.getAccessCode(), ConnectorAccessCode.qrPayload(connector.getAccessCode()));
    }
}
