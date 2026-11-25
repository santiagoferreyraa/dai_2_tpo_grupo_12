package com.ecopedia.async.stats.domain;

/** Rango de fechas inválido en la consulta de estadísticas ({@code from > to}). */
public class InvalidDateRangeException extends IllegalArgumentException {

    public static final String CODE = "INVALID_DATE_RANGE";

    public InvalidDateRangeException() {
        super("La fecha de inicio no puede ser posterior a la fecha de fin.");
    }
}
