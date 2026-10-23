package com.ecopedia.simulation.paymentgateway.web.dto;

import java.util.List;

/**
 * El cuerpo de un error, con la forma de los de Mercado Pago: el {@code code} de {@code cause} es
 * lo que Pagos lee para decidir qué decirle al conductor; el {@code message} es para el log.
 */
public record ErrorResponse(int status, String error, String message, List<Cause> cause) {

    public record Cause(String code, String description) {}

    public static ErrorResponse of(int status, String error, String code, String message) {
        return new ErrorResponse(status, error, message, List.of(new Cause(code, message)));
    }
}
