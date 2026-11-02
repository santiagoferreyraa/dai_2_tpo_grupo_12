package com.ecopedia.simulation.charger.web.dto;

/** El cuerpo de un error: el {@code code} es lo que lee Terminales; el {@code message}, para el log. */
public record ErrorResponse(int status, String code, String message) {}
