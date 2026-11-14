package com.ecopedia.charging.session.domain;

import java.util.Optional;

/**
 * Puerto hacia Terminales (ecopedia-core) para saber qué conector abre un código de acceso (RF11,
 * ECO-44).
 *
 * <p>El código es de Terminales —lo asigna al dar de alta el conector y lo imprime el operador—, así
 * que SesionesDeCarga no lo guarda: lo pregunta por la ruta interna, con la clave de servicio.
 */
public interface AccessCodeDirectory {

    /**
     * @param code el PIN de 6 dígitos, ya validado con {@link TerminalAccessCode}
     * @return el id del conector, o vacío si ningún conector tiene ese código
     * @throws ChargingPointCatalogUnavailableException si core no contestó
     */
    Optional<Long> findConnectorId(String code);
}
