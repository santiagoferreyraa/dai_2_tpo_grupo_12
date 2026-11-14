package com.ecopedia.core.terminal.domain;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El código de acceso de un conector: un PIN de 6 dígitos (RF11, ECO-44).
 *
 * <p>La terminal lo muestra de dos formas, y las dos dicen lo mismo: impreso, para tipearlo, y en un
 * QR cuyo contenido es {@code ecopedia:connector:<código>}. El prefijo existe para que un QR ajeno
 * —el de un menú, el de otra red de carga— no se confunda con un PIN que casualmente tenga seis
 * dígitos.
 *
 * <p><b>Al azar y no derivado del id.</b> El código prueba presencia física: si se pudiera calcular
 * desde el id del conector, que se ve en el mapa, cualquiera lo iniciaría desde su casa. Por eso
 * {@link SecureRandom} y no {@code Random}.
 */
public final class ConnectorAccessCode {

    public static final int LENGTH = 6;

    /** Lo que va adelante del PIN en el contenido del QR. */
    public static final String QR_PREFIX = "ecopedia:connector:";

    private static final Pattern PIN = Pattern.compile("\\d{" + LENGTH + "}");
    private static final Pattern QR =
            Pattern.compile(Pattern.quote(QR_PREFIX) + "(\\d{" + LENGTH + "})", Pattern.CASE_INSENSITIVE);
    private static final SecureRandom RANDOM = new SecureRandom();

    private ConnectorAccessCode() {}

    /** Un código nuevo, con ceros a la izquierda si hace falta: "004213" es tan válido como "904213". */
    public static String random() {
        return String.format("%0" + LENGTH + "d", RANDOM.nextInt((int) Math.pow(10, LENGTH)));
    }

    /** El contenido del QR de un código. */
    public static String qrPayload(String code) {
        return QR_PREFIX + code;
    }

    /**
     * El PIN que trae lo que escaneó o tipeó el conductor, o vacío si no es ni un PIN ni el QR de
     * una terminal. Acepta espacios alrededor, porque es lo que deja un copiar y pegar.
     */
    public static Optional<String> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.strip();
        if (PIN.matcher(value).matches()) {
            return Optional.of(value);
        }
        Matcher qr = QR.matcher(value);
        return qr.matches() ? Optional.of(qr.group(1)) : Optional.empty();
    }
}
