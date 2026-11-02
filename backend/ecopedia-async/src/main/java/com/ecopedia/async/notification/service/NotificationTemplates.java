package com.ecopedia.async.notification.service;

import com.ecopedia.async.notification.domain.NotificationToDispatch;
import com.ecopedia.async.notification.domain.StationInfo;
import java.time.Duration;
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
 *
 * <p><b>Cuando hay seña, el aviso dice qué pasó con ella, sin el monto</b> (ECO-53, ECO-57): "te la
 * reintegramos" o "no se devuelve". El monto está en la reserva, y Manuel pidió el 31/10 que los avisos
 * no lo repitan. El plazo de devolución no está escrito acá: sale del
 * propio evento ({@code depositRefundableUntil}), así que cambiarlo en Reservas no deja el aviso mintiendo.
 */
class NotificationTemplates {

    private static final Locale SPANISH = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d/MM", SPANISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", SPANISH);

    /** El largo de la columna {@code notifications.body}. */
    static final int MAX_BODY = 500;

    private final ZoneId zone;

    NotificationTemplates(ZoneId zone) {
        this.zone = zone;
    }

    String title(NotificationToDispatch request) {
        return switch (request.type()) {
            case BOOKING_CONFIRMED -> "Reserva confirmada";
            case BOOKING_CANCELLED -> "Reserva cancelada";
            case BOOKING_NO_SHOW -> "No te presentaste a tu reserva";
        };
    }

    /**
     * El cuerpo, que entra siempre en la columna ({@value #MAX_BODY} caracteres). Si con la dirección
     * no entra, va sin ella; y si ni así, se corta. Un aviso que no se puede guardar vuelve a la cola
     * una y otra vez hasta la DLQ, y el conductor no se entera de nada: mejor uno más corto.
     */
    String body(NotificationToDispatch request, StationInfo station) {
        String full = compose(request, where(request, station, true));
        if (full.length() <= MAX_BODY) {
            return full;
        }
        String shorter = compose(request, where(request, station, false));
        return shorter.length() <= MAX_BODY ? shorter : shorter.substring(0, MAX_BODY - 1) + "…";
    }

    private String compose(NotificationToDispatch request, String where) {
        String when = when(request);
        return switch (request.type()) {
            case BOOKING_CONFIRMED -> "Tu reserva " + where + " está confirmada para el " + when + ".";
            case BOOKING_CANCELLED ->
                "Cancelaste tu reserva " + where + " del " + when + ". " + cancelledDeposit(request);
            case BOOKING_NO_SHOW ->
                "No llegaste a tu reserva " + where + " del " + when
                        + ". Pasaron los 15 minutos de tolerancia, así que la cancelamos"
                        + (request.hasDeposit() ? " y la seña no se devuelve" : "")
                        + ". Ya podés hacer otra reserva.";
        };
    }

    /** Qué pasó con la seña al cancelar. Sin seña, lo de siempre: el horario quedó libre. */
    private String cancelledDeposit(NotificationToDispatch request) {
        if (!request.hasDeposit() || request.depositRefunded() == null) {
            return "El horario quedó libre.";
        }
        if (request.depositRefunded()) {
            return "Te reintegramos la seña.";
        }
        return "Como faltaban menos de " + noticeHours(request) + " para el inicio, la seña no se devuelve.";
    }

    /** "5 horas", calculado del plazo que viajó en el evento. */
    private static String noticeHours(NotificationToDispatch request) {
        if (request.depositRefundableUntil() == null) {
            return "el plazo de devolución";
        }
        long hours = Duration.between(request.depositRefundableUntil(), request.windowStart())
                .toHours();
        return hours == 1 ? "1 hora" : hours + " horas";
    }

    private String where(NotificationToDispatch request, StationInfo station, boolean withAddress) {
        if (station == null) {
            return "del conector " + request.connectorId();
        }
        String place = "en " + station.name();
        if (withAddress && station.address() != null && !station.address().isBlank()) {
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
