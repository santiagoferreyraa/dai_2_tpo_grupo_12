package com.ecopedia.integration.payment.domain;

import java.util.Set;

/**
 * Estado de una deuda o cobro pendiente del conductor (RF02, RF14, ECO-54, ECO-62).
 *
 * <pre>
 *   PENDING ──── un reintento o el conductor la paga ────► SETTLED
 *      │
 *      └── fallan los 3 reintentos ──► DEFAULTED ── el conductor la paga ──► SETTLED
 * </pre>
 */
public enum PendingPaymentStatus {
    /**
     * Deuda pendiente de cobro: bloquea reservar y habilitar, y el barrido la reintenta cada 20 minutos
     * (ECO-62).
     */
    PENDING,
    /** Deuda saldada: por un reintento o porque el conductor la pagó desde el perfil. */
    SETTLED,
    /** Deuda cancelada o condonada administrativamente. */
    CANCELLED,
    /**
     * Vencida: fallaron los tres reintentos (ECO-62). La cuenta queda marcada —se anulan sus reservas
     * confirmadas y no puede reservar ni habilitar—, ya no se reintenta sola y solo se salda cuando el
     * conductor la paga desde el perfil.
     */
    DEFAULTED;

    /** Las que el conductor todavía debe: las dos lo bloquean. */
    public static final Set<PendingPaymentStatus> OPEN = Set.of(PENDING, DEFAULTED);

    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
