package com.ecopedia.charging.session.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que escaneó o tipeó el conductor frente a la terminal (RF11, ECO-44): el PIN de 6 dígitos solo,
 * o el contenido del QR, {@code ecopedia:connector:<PIN>}.
 *
 * <p>Repite el formato que define {@code ConnectorAccessCode} en core, y es a propósito: los dos
 * artefactos no comparten clases (RNF09), y validarlo acá ahorra una llamada a core por cada código
 * mal escrito. Core lo vuelve a validar igual: la regla que manda es la suya.
 */
public final class TerminalAccessCode {

    private static final Pattern PIN = Pattern.compile("\\d{6}");
    private static final Pattern QR = Pattern.compile("ecopedia:connector:(\\d{6})", Pattern.CASE_INSENSITIVE);

    private TerminalAccessCode() {}

    /** El PIN que trae lo escaneado o tipeado, o vacío si no tiene la forma de un código de terminal. */
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
