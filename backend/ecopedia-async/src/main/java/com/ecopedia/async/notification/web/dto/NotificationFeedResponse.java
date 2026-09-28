package com.ecopedia.async.notification.web.dto;

import com.ecopedia.async.notification.domain.NotificationFeed;
import java.util.List;

/** La respuesta de {@code GET /api/notifications}: los avisos y el número de la campanita. */
public record NotificationFeedResponse(List<NotificationResponse> items, long unreadCount) {

    public static NotificationFeedResponse fromDomain(NotificationFeed feed) {
        return new NotificationFeedResponse(
                feed.items().stream().map(NotificationResponse::fromDomain).toList(), feed.unreadCount());
    }
}
