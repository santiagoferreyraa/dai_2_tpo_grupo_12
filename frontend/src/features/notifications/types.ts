/**
 * Tipos de la feature Avisos.
 *
 * Hay dos juegos, separados por el mismo motivo que en Reservas (ver `bookings/types.ts`):
 *
 * - **Los del contrato**, que espejan los DTO de `ecopedia-async`
 *   (com.ecopedia.async.notification.web.dto). Las fechas viajan como texto ISO-8601 porque eso es
 *   lo que manda Jackson, y los nombres de campo son los del JSON tal cual.
 * - **Los de la pantalla**, con las fechas ya convertidas a `Date`. Son los únicos que salen del
 *   repositorio: ningún componente ve un texto ISO.
 */

/*
 * ---------------------------------------------------------------------------
 * Contrato REST
 * ---------------------------------------------------------------------------
 */

/**
 * De qué avisa un aviso. Equivale al enum `NotificationType`.
 *
 * Se escribe como unión de textos y no como enum de TypeScript porque es exactamente lo que llega
 * en el JSON. Hoy son dos; el día que el backend agregue una, esto es lo único que hay que tocar
 * del lado de los tipos, y `NOTIFICATION_LOOK` en `format.ts` avisa en tiempo de compilación que
 * falta decidir con qué dibujo se muestra.
 */
export type NotificationType = 'BOOKING_CONFIRMED' | 'BOOKING_CANCELLED'

/** Un aviso como lo devuelve el backend. Equivale a NotificationResponse. */
export interface NotificationResponse {
  id: number
  type: NotificationType
  title: string
  body: string
  bookingId: number | null
  connectorId: number | null
  stationId: number | null
  stationName: string | null
  windowStart: string | null
  windowEnd: string | null
  createdAt: string
  read: boolean
}

/** La respuesta de `GET /api/notifications`. Equivale a NotificationFeedResponse. */
export interface NotificationFeedResponse {
  items: NotificationResponse[]
  unreadCount: number
}

/*
 * ---------------------------------------------------------------------------
 * Pantalla
 * ---------------------------------------------------------------------------
 */

/**
 * Un aviso, listo para mostrar.
 *
 * **El texto viene armado del backend y no se rearma acá.** `title` y `body` los escribe
 * `NotificationTemplates`, que es el lugar donde está decidido cómo se le habla al conductor; que
 * la pantalla los rehiciera con los datos sueltos sería tener dos redacciones para el mismo aviso
 * y que se separen con el tiempo. Los datos sueltos —la estación, la ventana, la reserva— están
 * igual porque sirven para otra cosa: enlazar el aviso con su reserva.
 */
export interface Notification {
  id: number
  type: NotificationType
  title: string
  body: string
  bookingId: number | null
  connectorId: number | null
  stationId: number | null
  stationName: string | null
  windowStart: Date | null
  windowEnd: Date | null
  createdAt: Date
  read: boolean
}

/** Los avisos y el número de la campanita, ya traducidos. */
export interface NotificationFeed {
  items: Notification[]
  unreadCount: number
}
