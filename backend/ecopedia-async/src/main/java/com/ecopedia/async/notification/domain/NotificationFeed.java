package com.ecopedia.async.notification.domain;

import java.util.List;

/**
 * Lo que ve un conductor al consultar sus avisos: los avisos pedidos y cuántos tiene sin leer.
 *
 * <p>Van juntos porque la pantalla los necesita juntos —la lista y el número de la campanita— y así
 * alcanza una sola consulta periódica en vez de dos.
 */
public record NotificationFeed(List<Notification> items, long unreadCount) {}
