import { useNow } from '@/features/bookings/useNow'
import { TrashIcon } from '@/features/navigation/icons'

import {
  markNotificationRead,
  removeNotification,
  useNotifications,
} from './data/notificationsStore'
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
 * Un aviso de la lista: lo que dice, cuándo llegó, si está sin leer y el tacho para borrarlo.
 */
function NotificationRow({ notification, now }: { notification: Notification; now: Date }) {
  const { Icon, tint } = notificationLook(notification.type)

  return (
    /*
      Un contenedor con DOS botones adentro y no un botón grande, por lo mismo que el recuadro
      flotante: acá hay dos acciones —marcar como leído y borrar— y un botón dentro de otro no es
      HTML válido. El borde y el fondo se mudaron del botón a este contenedor, así que el renglón
      se sigue viendo como una sola pieza; lo que se perdió es el `hover` sobre todo el renglón, y
      está recuperado con `group-hover` acá.

      El fondo es el de una reserva del historial, no el vidrio de una tarjeta: mismo
      `bg-surface/30`, mismo borde y mismo radio que la fila de `MyBookings`. Las dos secciones
      del perfil son listas de cosas que pasaron, una adentro de la misma tarjeta, y con dos
      superficies distintas se leían como dos componentes de sistemas distintos apoyados en el
      mismo lugar.

      De paso se va el problema que tenía el vidrio acá: `.glass-panel` trae una sombra pensada
      para una tarjeta suelta sobre la página, y debajo de cada renglón se leía como un corte.
      Un fondo plano con borde no tiene nada que despegar.
    */
    <div className="border-border bg-surface/30 hover:border-primary/60 group flex items-start gap-3 rounded-2xl border px-4 py-3 transition-colors">
      <Icon className={`${tint} mt-0.5 h-5 w-5 shrink-0`} />

      {/*
        **Es un botón y no un renglón muerto**, aunque no navegue a ningún lado: tocarlo lo marca
        como leído. Es el gesto que ya tiene aprendido cualquiera que use un buzón, y sin él la
        única forma de bajar el número de la campanita sería el "marcar todas", que es todo o nada.

        El aviso ya leído no se apaga ni se achica: sigue siendo la misma información y esconderla a
        medias es una forma de perderla. Lo único que cambia es el punto de la derecha, que es lo
        que de verdad significa "sin leer", y el peso del título.
      */}
      <button
        type="button"
        onClick={() => markNotificationRead(notification.id)}
        aria-label={notification.read ? undefined : `${notification.title}. Marcar como leído`}
        className="focus-visible:outline-primary min-w-0 flex-1 cursor-pointer rounded-lg text-left focus-visible:outline-2"
      >
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
      </button>

      {/*
        El punto de sin leer. Va a la derecha y no a la izquierda por una razón de lectura:
        alineado de ese lado, la columna de puntos se recorre de un vistazo sin que los títulos se
        corran de lugar según tengan punto o no.

        Ahora comparte el borde derecho con el tacho, y por eso tiene su lugar reservado también
        cuando no se dibuja: sin el hueco, marcar un aviso como leído corre el tacho dos píxeles y
        el dedo que iba a borrar el de abajo termina en otro lado.
      */}
      <span aria-hidden="true" className="flex w-2 shrink-0 justify-center">
        {!notification.read && <span className="brand-fill mt-1.5 h-2 w-2 rounded-full" />}
      </span>

      {/*
        El tacho. Borra sin preguntar, a diferencia de "Borrar todas": lo que se va es UN aviso que
        está a la vista, el gesto es del tamaño de su consecuencia, y un cartel de confirmación por
        cada renglón de un buzón convierte limpiar diez avisos en veinte toques.

        **En el celular está siempre visible y en PC aparece al pasar por encima.** No es un
        capricho de tamaño: en una pantalla táctil no existe el "pasar por encima", así que un
        tacho escondido detrás de un `hover` sería un botón que no se puede descubrir. El
        `md:focus-visible:opacity-100` es lo mismo para quien navega con el teclado, que tampoco pasa
        por encima de nada. Va con `md:` adelante y no suelto a propósito: las variantes de pantalla
        se generan al final de la hoja, así que un `focus-visible:opacity-100` sin `md:` quedaría
        ANTES del `md:opacity-0` y perdería contra él —el tacho no aparecería nunca al tabular—.
        Adentro del mismo `md:`, en cambio, gana por especificidad, que es lo que ya hace que
        funcione el `md:group-hover:` de al lado.

        El título va en la etiqueta accesible: con diez tachos en la lista, diez botones que dicen
        "Borrar el aviso" no le dicen a un lector de pantalla cuál es cuál.
      */}
      <button
        type="button"
        onClick={() => removeNotification(notification.id)}
        aria-label={`Borrar el aviso: ${notification.title}`}
        className="text-text-muted hover:text-danger focus-visible:outline-primary md:group-hover:opacity-100 md:focus-visible:opacity-100 -mt-1 -mr-1.5 flex h-7 w-7 shrink-0 cursor-pointer items-center justify-center rounded-lg transition-[color,opacity] focus-visible:outline-2 md:opacity-0"
      >
        <TrashIcon className="h-4 w-4" />
      </button>
    </div>
  )
}

/** Lo que ocupa el lugar de la lista cuando no hay lista: vacía, cargando o fallada. */
function Placeholder({ children }: { children: React.ReactNode }) {
  return <p className="text-text-muted py-6 text-center text-sm">{children}</p>
}
