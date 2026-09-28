package com.ecopedia.async.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Un aviso que se le dio a un conductor: lo que dice, de qué reserva habla y si ya lo leyó.
 *
 * <p>El texto se guarda ya armado. Ver la migración {@code create_notifications}: un aviso es el
 * registro de lo que se le dijo al conductor, no una vista que se recalcula.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 120, unique = true)
    private String eventId;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "body", nullable = false, length = 500)
    private String body;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "connector_id", nullable = false)
    private Long connectorId;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "station_id")
    private Long stationId;

    @Column(name = "station_name", length = 200)
    private String stationName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    /** Para JPA. */
    protected Notification() {}

    public Notification(
            NotificationToDispatch request, StationInfo station, String title, String body, Instant createdAt) {
        this.eventId = request.eventId();
        this.recipientId = request.recipientId();
        this.type = request.type();
        this.title = title;
        this.body = body;
        this.bookingId = request.bookingId();
        this.connectorId = request.connectorId();
        this.windowStart = request.windowStart();
        this.windowEnd = request.windowEnd();
        this.stationId = station != null ? station.stationId() : null;
        this.stationName = station != null ? station.name() : null;
        this.createdAt = createdAt;
    }

    /** Marca el aviso como leído. Si ya lo estaba, conserva la primera lectura. */
    public void markReadAt(Instant when) {
        if (readAt == null) {
            readAt = when;
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    public boolean belongsTo(Long userId) {
        return recipientId.equals(userId);
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Long getConnectorId() {
        return connectorId;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public Long getStationId() {
        return stationId;
    }

    public String getStationName() {
        return stationName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
