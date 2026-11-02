package com.ecopedia.core.terminal.domain;

/**
 * El cargador no contestó: caído, más lento que el timeout, rechazó la clave de acceso, o no pudo
 * publicar su telemetría y por eso no encendió.
 *
 * <p>Qué ve el conductor en ese caso se decide con ECO-44, junto con la habilitación. El mensaje está
 * escrito para el log, no para la pantalla (directriz 7).
 */
public class ChargerUnavailableException extends RuntimeException {

    public ChargerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
