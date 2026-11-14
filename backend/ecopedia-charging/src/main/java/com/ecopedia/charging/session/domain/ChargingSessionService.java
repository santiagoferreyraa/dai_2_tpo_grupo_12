package com.ecopedia.charging.session.domain;

import java.util.Optional;

/**
 * Interfaz publicada del componente SesionesDeCarga: el ciclo de vida de una carga, desde que se
 * habilita hasta que se cierra y se liquida.
 *
 * <p>Quien lo consume —el controlador REST y el barrido que corta las sesiones vencidas— depende de
 * esta interfaz y no de su implementación, como en el resto de los componentes.
 */
public interface ChargingSessionService {

    /**
     * Habilita una carga (RF11): valida, persiste la sesión y enciende el cargador. Sin reserva en
     * el pedido, registra una carga sin reserva (RF10).
     *
     * @param authToken el token del conductor, para las consultas que se hacen en su nombre
     */
    ActivationResult activate(ActivationRequest request, String authToken);

    /**
     * Detiene la sesión del conductor, libera el conector y liquida el consumo (RF12, RF14). Una
     * sesión de otro conductor se informa igual que una que no existe.
     *
     * <p>Los kWh salen del cargador (o, si no contesta, de la última lectura de la telemetría): el
     * conductor no los informa (ECO-44). Detener una sesión que ya cerró —un reintento, o el corte
     * automático que llegó primero— devuelve la liquidación que quedó guardada, sin volver a cobrar.
     */
    SessionStopResult stopSession(String sessionId, Long driverId);

    /**
     * Una sesión del conductor, activa o terminada (ECO-44). Una de otro conductor se informa igual
     * que una que no existe.
     *
     * @throws SessionNotFoundException si no existe o es de otro conductor
     */
    ChargingSessionView getSession(String sessionId, Long driverId);

    /** La sesión activa del conductor, si tiene una (ECO-44). */
    Optional<ChargingSessionView> findActiveSession(Long driverId);

    /**
     * Corta las sesiones activas cuya ventana ya venció, la reservada o la de la carga sin reserva.
     *
     * @return cuántas sesiones cortó
     */
    int stopExpiredSessions();

    /**
     * Guarda en la sesión lo que informa el cargador: kWh acumulados, potencia y porcentaje de
     * batería (RF16). Una lectura de una sesión que no existe o ya cerró, o que llega después de una
     * más nueva, se descarta sin error.
     *
     * <p>Si es la última lectura —el cargador avisa que cortó por batería llena o por falla—, queda
     * registrado el motivo en la sesión, que sigue abierta: cerrarla y liquidarla es otra decisión.
     *
     * @return {@code true} si la lectura se guardó, {@code false} si se descartó
     * @throws IllegalArgumentException si a la lectura le falta la sesión, el instante o los kWh
     */
    boolean processTelemetry(TelemetryReading reading);
}
