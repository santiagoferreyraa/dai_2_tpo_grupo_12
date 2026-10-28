package com.ecopedia.simulation.charger.domain;

import org.springframework.http.HttpStatus;

/**
 * Una orden que el cargador no cumple, con el estado HTTP y un {@code code} estable.
 *
 * <p>El {@code code} es el contrato: Terminales decide qué hacer según el código, no según el texto,
 * que va en inglés, como el de la API de cualquier fabricante.
 */
public class ChargerException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ChargerException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ChargerException conflict(String code, String message) {
        return new ChargerException(HttpStatus.CONFLICT, code, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
