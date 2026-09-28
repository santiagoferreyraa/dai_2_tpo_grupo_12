import { useNow } from '@/features/bookings/useNow'

import { markNotificationRead, useNotifications } from './data/notificationsStore'
import { notificationLook, timeAgo } from './format'
import type { Notification } from './types'

/**
 * La lista de avisos, escrita una vez para los dos lugares donde se muestra.
 *
 * La usan la ventanita que cuelga de la campanita y la sección "Notificaciones" del perfil. Son
 * dos marcos distintos —una ventanita corta y con scroll, una tarjeta a lo ancho de la pantalla—
 * pero lo de adentro es la misma lista, y partirla en dos garantizaría que un día digan cosas
 * distintas.
 *
 * **No pide datos.** Lee el buzón compartido (`notificationsStore`), así que abrir el perfil con
 * la ventanita abierta no dispara una segunda consulta ni puede mostrar dos estados distintos del
 * mismo buzón.
 */

interface NotificationListProps {
  /**
   * Hasta cuántos mostrar. Sin esto, todos.
   *
   * Lo usa la ventanita: ahí la lista es un asomo de lo último, y quien quiera el historial entero
   * tiene el enlace al perfil abajo. La tarjeta del perfil no pasa nada y muestra todo.
   */
  limit?: number
  /** Qué decir cuando no hay ninguno. Cambia según el marco, que es lo único que cambia. */
  emptyHint: string
}

export default function NotificationList({ limit, emptyHint }: NotificationListProps) {
  const state = useNotifications()

  /*
   * El reloj corre cada medio minuto porque lo más fino que dice la lista es el minuto: con el
   * segundo se redibujaría sesenta veces para escribir el mismo texto.
   */
  const now = useNow(30_000)

  if (state.status === 'idle') {
    return <Placeholder>Entrá para ver tus avisos.</Placeholder>
  }

  if (state.status === 'loading') {
    return <Placeholder>Cargando tus avisos…</Placeholder>
  }

  if (state.status === 'error') {
    return <Placeholder>{state.message}</Placeholder>
  }

  if (state.items.length === 0) {
    return <Placeholder>{emptyHint}</Placeholder>
  }

  const items = limit === undefined ? state.items : state.items.slice(0, limit)

  return (
    <ul className="flex flex-col gap-2">
      {items.map((notification) => (
        <li key={notification.id}>
          <NotificationRow notification={notification} now={now} />
        </li>
      ))}
    </ul>
  )
}

/**
 * Un aviso de la lista.
 *
 * **Es un botón y no un renglón muerto**, aunque no navegue a ningún lado: tocarlo lo marca como
 * leído. Es el gesto que ya tiene aprendido cualquiera que use un buzón, y sin él la única forma
 * de bajar el número de la campanita sería el "marcar todas", que es todo o nada.
 *
 * El aviso ya leído no se apaga ni se achica: sigue siendo la misma información y esconderla a
 * medias es una forma de perderla. Lo único que cambia es el punto de la izquierda, que es lo que
 * de verdad significa "sin leer", y el peso del título.
 */
function NotificationRow({ notification, now }: { notification: Notification; now: Date }) {
  const { Icon, tint } = notificationLook(notification.type)

  return (
    <button
      type="button"
      onClick={() => markNotificationRead(notification.id)}
      aria-label={notification.read ? undefined : `${notification.title}. Marcar como leído`}
      /*
        El fondo es el de una reserva del historial, no el vidrio de una tarjeta: mismo
        `bg-surface/30`, mismo borde y mismo radio que la fila de `MyBookings`. Las dos secciones
        del perfil son listas de cosas que pasaron, una adentro de la misma tarjeta, y con dos
        superficies distintas se leían como dos componentes de sistemas distintos apoyados en el
        mismo lugar.

        De paso se va el problema que tenía el vidrio acá: `.glass-panel` trae una sombra pensada
        para una tarjeta suelta sobre la página, y debajo de cada renglón se leía como un corte.
        Un fondo plano con borde no tiene nada que despegar.
      */
      className="border-border bg-surface/30 hover:border-primary/60 focus-visible:outline-primary flex w-full cursor-pointer items-start gap-3 rounded-2xl border px-4 py-3 text-left transition-colors focus-visible:outline-2"
    >
      <Icon className={`${tint} mt-0.5 h-5 w-5 shrink-0`} />

      <span className="min-w-0 flex-1">
        <span className="flex items-baseline gap-2">
          <span
            className={`text-text flex-1 text-sm ${notification.read ? 'font-medium' : 'font-bold'}`}
          >
            {notification.title}
          </span>
          {/*
            La hora no se abrevia ni se parte: `shrink-0` es lo que evita que "Hace 5 min" quede
            en dos renglones cuando el título es largo, que es siempre.
          */}
          <span className="text-text-muted shrink-0 text-[11px]">
            {timeAgo(notification.createdAt, now)}
          </span>
        </span>

        <span className="text-text-muted mt-0.5 block text-xs leading-relaxed">
          {notification.body}
        </span>
      </span>

      {/*
        El punto de sin leer. Va al final y no al principio por una razón de lectura: alineado a la
        derecha, la columna de puntos se recorre de un vistazo sin que los títulos se corran de
        lugar según tengan punto o no.
      */}
      {!notification.read && (
        <span aria-hidden="true" className="brand-fill mt-1.5 h-2 w-2 shrink-0 rounded-full" />
      )}
    </button>
  )
}

/** Lo que ocupa el lugar de la lista cuando no hay lista: vacía, cargando o fallada. */
function Placeholder({ children }: { children: React.ReactNode }) {
  return <p className="text-text-muted py-6 text-center text-sm">{children}</p>
}
