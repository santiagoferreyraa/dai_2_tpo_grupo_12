package com.ecopedia.integration.powergrid.domain;

/**
 * La distribuidora no contestó: caída, más lenta que el timeout, o rechazó las credenciales.
 *
 * <p>Se separa de un rechazo porque quien llama tiene que hacer otra cosa. Qué hace SesionesDeCarga
 * cuando la distribuidora no contesta —el diseño dice cargar en potencia normal— se decide con
 * ECO-52, junto con lo que ve el conductor. El mensaje está escrito para el log, no para la
 * pantalla (directriz 7).
 */
public class PowerGridUnavailableException extends RuntimeException {

    public PowerGridUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
