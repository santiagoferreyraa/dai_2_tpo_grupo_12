package com.ecopedia.integration.payment.domain;

/**
 * Qué le pasó a una deuda del conductor, para avisarle (ECO-62).
 *
 * <p>Los nombres viajan como texto en el mensaje y coinciden con los de {@code NotificationType} en
 * {@code ecopedia-async}: son dos enums, uno por artefacto, como los de las reservas.
 */
public enum DebtEventType {
    /** El cobro al cerrar la carga falló y nació la deuda. */
    DEBT_RECORDED,
    /** Un reintento del barrido falló y todavía quedan otros. */
    DEBT_RETRY_FAILED,
    /** Falló el último reintento: la cuenta queda marcada y se anulan sus reservas. */
    DEBT_DEFAULTED,
    /** Se cobró: por un reintento o porque el conductor la pagó desde el perfil. */
    DEBT_SETTLED
}
