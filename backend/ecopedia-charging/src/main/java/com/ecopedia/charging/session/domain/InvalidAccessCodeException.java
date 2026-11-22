package com.ecopedia.charging.session.domain;

/**
 * El código escaneado o tipeado no abre ningún conector, o abre otro que el que el conductor dijo
 * (RF11, ECO-44).
 *
 * <p>Un solo motivo hacia afuera para los tres casos —mal escrito, de nadie, de otro conector—: a
 * quien está frente a la terminal le alcanza con "revisá el código", y a quien prueba códigos al azar
 * no le conviene saber cuáles tienen la forma correcta.
 */
public class InvalidAccessCodeException extends RuntimeException {

    public InvalidAccessCodeException(String reason) {
        super(reason);
    }
}
