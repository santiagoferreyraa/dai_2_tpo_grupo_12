package com.ecopedia.async.notification.service;

import com.ecopedia.async.notification.domain.DebtFacts;
import com.ecopedia.async.notification.domain.NotificationToDispatch;
import com.ecopedia.async.notification.domain.NotificationType;
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
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM", SPANISH);

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
            case BOOKING_CANCELLED_FOR_DEBT -> "Anulamos tu reserva";
            case BOOKING_ACTIVATION_FAILED -> "No pudimos iniciar tu carga";
            case DEBT_RECORDED -> "No pudimos cobrar tu carga";
            case DEBT_RETRY_FAILED -> "No pudimos cobrar tu carga" + attemptOf(request);
            case DEBT_DEFAULTED -> "Tu cuenta tiene un pago vencido";
            case DEBT_SETTLED -> "Pago recibido";
        };
    }

    /**
     * El cuerpo, que entra siempre en la columna ({@value #MAX_BODY} caracteres). Si con la dirección
     * no entra, va sin ella; y si ni así, se corta. Un aviso que no se puede guardar vuelve a la cola
     * una y otra vez hasta la DLQ, y el conductor no se entera de nada: mejor uno más corto.
     */
    String body(NotificationToDispatch request, StationInfo station) {
        if (request.debt() != null) {
            return debtBody(request, request.debt());
        }
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
            case BOOKING_CANCELLED_FOR_DEBT ->
                "Anulamos tu reserva " + where + " del " + when + " porque no pudimos cobrar tu última carga."
                        + (request.hasDeposit() ? " Te reintegramos la seña." : "")
                        + " Pagá el saldo desde Medios de pago para volver a reservar.";
            // Dice qué hacer, no qué falló por detrás (Manuel, 02/11 y 25/11).
            case BOOKING_ACTIVATION_FAILED ->
                "No pudimos iniciar la carga de tu reserva " + where + " del " + when + "."
                        + (request.hasDeposit() ? " Te reintegramos la seña." : "")
                        + " Podés reservar otro horario o elegir otra estación.";
            case DEBT_RECORDED, DEBT_RETRY_FAILED, DEBT_DEFAULTED, DEBT_SETTLED ->
                throw new IllegalArgumentException("Un aviso de deuda sin los datos de la deuda: " + request.eventId());
        };
    }

    /**
     * Los avisos de una deuda (ECO-62): no son de ninguna reserva, así que no dicen dónde ni a qué hora,
     * sino de qué carga. Sin el monto, como los de la seña (Manuel, 31/10): está en Medios de pago.
     */
    private String debtBody(NotificationToDispatch request, DebtFacts debt) {
        String charge = "la carga del " + DATE.format(debt.incurredAt().atZone(zone));
        return switch (request.type()) {
            case DEBT_RECORDED ->
                "No pudimos cobrar " + charge + ". Lo vamos a reintentar " + untilNextAttempt(debt)
                        + ". Hasta que se pague, no podés reservar ni cargar.";
            case DEBT_RETRY_FAILED ->
                "Volvimos a intentar cobrar " + charge + " y no se pudo. El próximo intento es "
                        + untilNextAttempt(debt) + ". Podés pagarla ahora desde Medios de pago.";
            case DEBT_DEFAULTED ->
                "Hicimos el último intento" + attemptOf(request) + " y no pudimos cobrar " + charge
                        + ". Si tenías reservas, las anulamos y te devolvimos sus señas."
                        + " Para volver a reservar, pagala desde Medios de pago.";
            case DEBT_SETTLED -> "Cobramos " + charge + ". Ya podés volver a reservar.";
            case BOOKING_CONFIRMED,
                    BOOKING_CANCELLED,
                    BOOKING_NO_SHOW,
                    BOOKING_CANCELLED_FOR_DEBT,
                    BOOKING_ACTIVATION_FAILED ->
                throw new IllegalArgumentException("Un aviso de reserva con datos de deuda: " + request.eventId());
        };
    }

    /** " (reintento 1 de 3)", o nada si el evento no trae los números. */
    private static String attemptOf(NotificationToDispatch request) {
        DebtFacts debt = request.debt();
        if (debt == null || debt.attempt() == null || debt.maxAttempts() == null) {
            return "";
        }
        return request.type() == NotificationType.DEBT_DEFAULTED
                ? " (" + debt.attempt() + " de " + debt.maxAttempts() + ")"
                : " (reintento " + debt.attempt() + " de " + debt.maxAttempts() + ")";
    }

    /**
     * "en 20 minutos", calculado de lo que viajó en el evento. El intervalo no está escrito acá: así
     * cambiarlo en Pagos no deja el aviso mintiendo.
     */
    private static String untilNextAttempt(DebtFacts debt) {
        if (debt.nextAttemptAt() == null || debt.occurredAt() == null) {
            return "más tarde";
        }
        long seconds = Duration.between(debt.occurredAt(), debt.nextAttemptAt()).toSeconds();
        if (seconds < 60) {
            return "en menos de un minuto";
        }
        long minutes = Math.round(seconds / 60.0);
        return minutes == 1 ? "en 1 minuto" : "en " + minutes + " minutos";
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
