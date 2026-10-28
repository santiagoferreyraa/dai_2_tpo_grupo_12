package com.ecopedia.core.terminal.domain;

/**
 * El cargador contestó, y dijo que no: la orden no se cumple.
 *
 * <p>Se separa de {@link ChargerUnavailableException} porque quien llama hace otra cosa: un conector
 * roto es "la máquina rota" de RF13, que reintegra la seña; uno que no contesta puede volver en un
 * rato. El {@link #getReason() motivo} dice cuál, para decidir sin leer el texto, que es para el log.
 */
public class ChargerRejectedException extends RuntimeException {

    /** Por qué el cargador no cumplió la orden. */
    public enum Reason {
        /** El conector está fuera de servicio en el hardware. */
        CONNECTOR_FAULTED,
        /** El conector está cargando otra sesión. */
        CONNECTOR_BUSY,
        /** El cargador no entendió la orden. Si pasa, es un error nuestro. */
        INVALID_REQUEST
    }

    private final Reason reason;

    public ChargerRejectedException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
