package com.ecopedia.integration.payment.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Un aviso de deuda que no pudo salir por la cola porque el broker no estaba (ECO-62). El mismo
 * reenvío que el de Reservas (ECO-177, QA-27).
 *
 * <p>Vive en el paquete de mensajería y no en el dominio de Pagos: no es un dato de la deuda, es la
 * parte de la infraestructura que garantiza que el aviso llegue. Pagos sigue publicando por
 * {@code DebtEventPublisher} sin enterarse de que existe.
 */
@Entity
@Table(name = "pending_notifications")
public class PendingNotification {

    /** El largo de la columna {@code last_error}. */
    static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 120)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "debt_id", nullable = false)
    private Long debtId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    /** Lo pide JPA. */
    protected PendingNotification() {}

    PendingNotification(String eventId, String eventType, Long debtId, String payload, Instant createdAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.debtId = debtId;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    /** Un intento más que falló, con el motivo recortado al largo de la columna. */
    void failedAgain(String reason) {
        attempts++;
        lastError =
                reason == null || reason.length() <= MAX_ERROR_LENGTH ? reason : reason.substring(0, MAX_ERROR_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getDebtId() {
        return debtId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
