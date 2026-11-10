package com.ecopedia.charging.session.domain;

import java.util.Optional;

/**
 * Interfaz del componente <i>SesionesDeCarga</i> hacia el conductor, después de la habilitación
 * (RF11, ECO-44). Ver ARQUITECTURA_ECOPEDIA.md §2.3, ficha 4.
 *
 * <p>La habilitación —escanear, validar, activar— vive en {@code ChargingActivationService}; acá está
 * el resto de la vida de la sesión: consultarla y terminarla.
 */
public interface ChargingSessionService {

    /**
     * Una sesión del conductor, activa o terminada.
     *
     * @throws ChargingSessionNotFoundException si no existe o es de otro conductor
     */
    ChargingSessionView getSession(String sessionId, Long driverId);

    /** La sesión activa del conductor, si tiene una. */
    Optional<ChargingSessionView> findActiveSession(Long driverId);

    /**
     * El conductor detiene su carga desde la aplicación (RF11).
     *
     * <p>Apaga el cargador, cierra la sesión, libera el conector y la ventana que quedaba, y después
     * cobra el consumo (RF14). El cierre no espera al cobro (RNF06): si Pagos o la pasarela fallan,
     * la sesión queda cerrada igual y el importe, como deuda o para reintentar.
     *
     * <p>Idempotente: detener una sesión ya terminada devuelve cómo terminó, sin volver a cobrar. El
     * front puede reintentar un pedido que se cortó sin preguntar antes cómo quedó.
     *
     * @throws ChargingSessionNotFoundException si no existe o es de otro conductor
     */
    ChargingSessionView stop(String sessionId, Long driverId);
}
