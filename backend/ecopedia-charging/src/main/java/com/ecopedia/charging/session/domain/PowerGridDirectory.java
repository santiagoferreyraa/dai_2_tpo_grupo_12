package com.ecopedia.charging.session.domain;

import java.math.BigDecimal;

/**
 * Puerto hacia RedEléctrica, que vive en ecopedia-integration (ECO-52).
 *
 * <p>Tres resultados, los mismos que da RedEléctrica: una respuesta (que puede ser "no"), un pedido
 * que la distribuidora rechazó, o nadie que conteste. Este puerto no decide nada con ellos: eso es
 * {@code ChargingPowerPolicy}.
 */
public interface PowerGridDirectory {

    /**
     * @throws GridRequestRejectedException si la distribuidora rechazó el pedido, con el motivo
     * @throws GridUnavailableException si RedEléctrica o la distribuidora no contestaron
     */
    GridFeasibility checkFeasibility(String nodeId, BigDecimal requestedPowerKw);
}
