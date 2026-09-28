import { api } from '@/lib/api'

import type {
  Notification,
  NotificationFeed,
  NotificationFeedResponse,
  NotificationResponse,
} from '../types'

/**
 * Acceso a datos de avisos: las llamadas a `/api/notifications` del frontend.
 *
 * Existe por lo mismo que `bookingsRepository` y `stationsRepository`: ninguna pantalla escribe
 * una ruta ni llama a `fetch`. Las rutas van sin `/api` porque lo agrega el cliente, y el proxy de
 * Vite las desvía a `ecopedia-async` (8084), que es otro proceso distinto de core y de reservas.
 *
 * **Lo único que agrega sobre el contrato es pasar las fechas a `Date`**, que es lo que la
 * pantalla necesita para decir "hace cinco minutos" y para ordenar.
 *
 * **Nunca se piden los avisos de un id.** El destinatario sale del token, así que no hay ruta que
 * permita leer los de otro; marcar uno como leído sí manda el id del AVISO, y el backend verifica
 * que sea tuyo antes de tocarlo.
 */

/**
 * Los avisos del conductor que pregunta, del más nuevo al más viejo, y cuántos hay sin leer.
 *
 * `after` es el id del aviso más nuevo que la pantalla ya tiene. Con él la respuesta trae **solo
 * lo que llegó después**, que es lo que convierte la consulta repetida en algo barato: casi
 * siempre vuelve con la lista vacía y el número de la campanita.
 *
 * El `unreadCount` viene entero igual, con `after` o sin él: es del buzón, no de la página.
 *
 * @param signal para cancelar la petición si la pantalla se desmonta antes de que responda.
 */
export async function listMyNotifications(
  after?: number,
  signal?: AbortSignal,
): Promise<NotificationFeed> {
  const response = await api.get<NotificationFeedResponse>('/notifications', {
    params: { after },
    signal,
  })

  return {
    items: response.items.map(toNotification),
    unreadCount: response.unreadCount,
  }
}

/**
 * Marca un aviso propio como leído. Responde 204 sin cuerpo.
 *
 * Errores que la pantalla tiene que tratar aparte:
 * - **404**: el aviso no existe.
 * - **403**: el aviso es de otro. No debería pasar desde la interfaz, que solo muestra los
 *   propios, y por eso no tiene un mensaje pensado: si aparece, hay algo mal más arriba.
 */
export function markNotificationAsRead(notificationId: number): Promise<void> {
  return api.post<void>(`/notifications/${String(notificationId)}/read`, undefined)
}

/** Marca como leídos todos los avisos propios de una sola vez. Responde 204 sin cuerpo. */
export function markAllNotificationsAsRead(): Promise<void> {
  return api.post<void>('/notifications/read-all', undefined)
}

/*
 * ---------------------------------------------------------------------------
 * Traducciones
 * ---------------------------------------------------------------------------
 */

/** Las tres fechas que pueden faltar: un aviso sin reserva asociada no tiene ventana. */
function toDate(value: string | null): Date | null {
  return value === null ? null : new Date(value)
}

function toNotification(response: NotificationResponse): Notification {
  return {
    ...response,
    windowStart: toDate(response.windowStart),
    windowEnd: toDate(response.windowEnd),
    createdAt: new Date(response.createdAt),
  }
}
