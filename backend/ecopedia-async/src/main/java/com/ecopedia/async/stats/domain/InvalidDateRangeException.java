package com.ecopedia.async.stats.domain;

/** Rango de fechas inválido en la consulta de estadísticas: invertido o demasiado largo. */
public class InvalidDateRangeException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public static final String CODE = "INVALID_DATE_RANGE";

    public InvalidDateRangeException(String message) {
        super(message);
    }

    public static InvalidDateRangeException inverted() {
        return new InvalidDateRangeException("La fecha de inicio no puede ser posterior a la fecha de fin.");
    }

    public static InvalidDateRangeException tooLong(long maxDays) {
        return new InvalidDateRangeException("El rango no puede superar los " + maxDays + " días.");
    }
}
