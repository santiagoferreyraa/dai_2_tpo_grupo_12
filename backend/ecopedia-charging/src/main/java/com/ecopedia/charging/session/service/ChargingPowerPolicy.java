package com.ecopedia.charging.session.service;

import com.ecopedia.charging.session.domain.ChargingPoint;
import com.ecopedia.charging.session.domain.GridFeasibility;
import com.ecopedia.charging.session.domain.GridRequestRejectedException;
import com.ecopedia.charging.session.domain.GridUnavailableException;
import com.ecopedia.charging.session.domain.PowerDecision;
import com.ecopedia.charging.session.domain.PowerDecision.Outcome;
import com.ecopedia.charging.session.domain.PowerGridDirectory;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Con qué potencia se habilita un conector: la regla de SesionesDeCarga frente a la distribuidora
 * (RF15, ECO-52). Decidida por Manuel el 28/10; está en ARQUITECTURA, ficha 7.
 *
 * <ol>
 *   <li><b>Hasta el umbral de carga rápida (22 kW) no se pregunta</b>: RF15 exige la consulta solo
 *       para la rápida.
 *   <li><b>Un "sí" carga a la potencia del conector.</b>
 *   <li><b>Un "no" usa el tope que trae la respuesta.</b> Si es mayor que 0, se carga hasta ahí; solo
 *       si es 0 la carga no puede iniciarse, y es la falla de RF13 (reintegro de la seña). Así, un "no"
 *       explícito nunca termina peor que no contestar.
 *   <li><b>Sin respuesta válida, potencia normal</b> (22 kW): la distribuidora caída o lenta, un nodo
 *       que no conoce, una estación sin nodo. La potencia normal no necesita autorización, y el
 *       conductor no se queda sin cargar por algo que no depende de él.
 * </ol>
 *
 * <p>Los tres casos de la regla 4 terminan igual para el conductor; lo que cambia es el log, que es
 * para el equipo: una estación sin nodo es lo normal en una que no ofrece carga rápida (nada que
 * avisar), un nodo desconocido es un dato mal cargado (WARN) y un pedido inválido es un bug nuestro
 * (ERROR).
 *
 * <p>Todavía no la llama nadie: la usa la habilitación (ECO-44), que es la que avisa al conductor de
 * la potencia reducida y hace el reintegro.
 */
@Service
public class ChargingPowerPolicy {

    private static final Logger log = LoggerFactory.getLogger(ChargingPowerPolicy.class);

    private final PowerGridDirectory powerGrid;
    private final BigDecimal fastChargeThresholdKw;
    private final BigDecimal normalPowerKw;

    public ChargingPowerPolicy(
            PowerGridDirectory powerGrid,
            @Value("${ecopedia.charging.fast-charge-threshold-kw}") BigDecimal fastChargeThresholdKw,
            @Value("${ecopedia.charging.normal-power-kw}") BigDecimal normalPowerKw) {
        this.powerGrid = powerGrid;
        this.fastChargeThresholdKw = fastChargeThresholdKw;
        this.normalPowerKw = normalPowerKw;
    }

    public PowerDecision decide(ChargingPoint point) {
        BigDecimal connectorPower = point.maxPowerKw();
        if (connectorPower.compareTo(fastChargeThresholdKw) <= 0) {
            return new PowerDecision(connectorPower, Outcome.STANDARD_CHARGE);
        }
        BigDecimal normal = normalPowerKw.min(connectorPower);

        if (point.nodeId() == null || point.nodeId().isBlank()) {
            return new PowerDecision(normal, Outcome.NO_FAST_CHARGE_AT_STATION);
        }

        GridFeasibility answer;
        try {
            answer = powerGrid.checkFeasibility(point.nodeId(), connectorPower);
        } catch (GridUnavailableException noAnswer) {
            log.warn(
                    "Sin respuesta de la distribuidora para la estación {} (nodo {}): conector {} a potencia normal",
                    point.stationId(),
                    point.nodeId(),
                    point.connectorId());
            return new PowerDecision(normal, Outcome.GRID_UNAVAILABLE);
        } catch (GridRequestRejectedException rejected) {
            if (GridRequestRejectedException.UNKNOWN_NODE.equals(rejected.getCode())) {
                log.warn(
                        "La distribuidora no conoce el nodo {} de la estación {}: revisar el dato de la estación."
                                + " Conector {} a potencia normal",
                        point.nodeId(),
                        point.stationId(),
                        point.connectorId());
                return new PowerDecision(normal, Outcome.UNKNOWN_NODE);
            }
            log.error(
                    "La distribuidora rechazó el pedido por {} kW del conector {} (nodo {}): {}. Va a potencia normal",
                    connectorPower,
                    point.connectorId(),
                    point.nodeId(),
                    rejected.getCode());
            return new PowerDecision(normal, Outcome.GRID_REQUEST_ERROR);
        }

        if (answer.feasible()) {
            return new PowerDecision(connectorPower, Outcome.FAST_AUTHORIZED);
        }
        BigDecimal cap = answer.maxAllowedPowerKw() == null ? BigDecimal.ZERO : answer.maxAllowedPowerKw();
        if (cap.signum() > 0) {
            log.info(
                    "La distribuidora limita el conector {} a {} kW (nodo {}, {})",
                    point.connectorId(),
                    cap,
                    point.nodeId(),
                    answer.reason());
            return new PowerDecision(cap.min(connectorPower), Outcome.LIMITED_BY_GRID);
        }
        log.info(
                "La distribuidora no autoriza el conector {} (nodo {}, {}): la carga no puede iniciarse",
                point.connectorId(),
                point.nodeId(),
                answer.reason());
        return new PowerDecision(BigDecimal.ZERO, Outcome.REJECTED_BY_GRID);
    }
}
