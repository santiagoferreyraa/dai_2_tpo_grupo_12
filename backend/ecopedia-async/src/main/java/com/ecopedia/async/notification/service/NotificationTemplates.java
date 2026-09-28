package com.ecopedia.async.notification.service;

import com.ecopedia.async.notification.domain.NotificationToDispatch;
import com.ecopedia.async.notification.domain.StationInfo;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * El texto de cada aviso: un título corto y una frase que dice dónde y cuándo.
 *
 * <p>Las horas se escriben en la zona del conductor y no en UTC, que es como viajan: "de 13:00 a
 * 14:00" en UTC es "de 10:00 a 11:00" en Buenos Aires, y un aviso con la hora corrida tres horas es
 * peor que ningún aviso.
 *
 * <p>Si core no contestó ({@code station} nulo), el aviso nombra el conector en vez de la estación.
 * Dice menos, pero no dice nada falso.
 */
class NotificationTemplates {

    private static final Locale SPANISH = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d/MM", SPANISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", SPANISH);

    private final ZoneId zone;

    NotificationTemplates(ZoneId zone) {
        this.zone = zone;
    }

    String title(NotificationToDispatch request) {
        return switch (request.type()) {
            case BOOKING_CONFIRMED -> "Reserva confirmada";
            case BOOKING_CANCELLED -> "Reserva cancelada";
        };
    }

    String body(NotificationToDispatch request, StationInfo station) {
        String where = where(request, station);
        String when = when(request);
        return switch (request.type()) {
            case BOOKING_CONFIRMED -> "Tu reserva " + where + " está confirmada para el " + when + ".";
            case BOOKING_CANCELLED -> "Cancelaste tu reserva " + where + " del " + when + ". El horario quedó libre.";
        };
    }

    private String where(NotificationToDispatch request, StationInfo station) {
        if (station == null) {
            return "del conector " + request.connectorId();
        }
        String place = "en " + station.name();
        if (station.address() != null && !station.address().isBlank()) {
            place += " (" + station.address() + ")";
        }
        if (station.connectorType() != null) {
            place += ", conector " + station.connectorType();
        }
        return place;
    }

    /** "miércoles 30/09 de 10:00 a 11:00", en la zona del conductor. */
    private String when(NotificationToDispatch request) {
        var start = request.windowStart().atZone(zone);
        var end = request.windowEnd().atZone(zone);
        return DAY.format(start) + " de " + TIME.format(start) + " a " + TIME.format(end);
    }
}
