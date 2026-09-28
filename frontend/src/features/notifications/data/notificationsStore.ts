import { useEffect, useSyncExternalStore } from 'react'

import { getSession, useSession } from '@/features/auth/session'

import type { Notification } from '../types'
import {
  listMyNotifications,
  markAllNotificationsAsRead,
  markNotificationAsRead,
} from './notificationsRepository'

/**
 * El buzón del conductor: los avisos, pedidos una vez y compartidos por quien los muestre.
 *
 * **Existe por lo mismo que `myBookingsStore`**, y acá el motivo aprieta más todavía: los avisos
 * se muestran en tres lugares a la vez —la campanita de la franja con su número, la lista que
 * cuelga de ella y la sección "Notificaciones" del perfil— y además hay un cuarto que no muestra
 * nada sino que reacciona: los recuadros que aparecen cuando llega uno nuevo. Con un pedido por
 * componente, abrir el perfil pediría el buzón dos veces y cada copia consultaría por su cuenta,
 * así que el mismo aviso podría llegar dos veces o no llegar a la campanita.
 *
 * **Se consulta cada tanto porque el navegador no habla con la cola.** El aviso nace en Artemis y
 * `ecopedia-async` lo guarda; del otro lado no hay nada que empuje. La consulta repetida es barata
 * a propósito: se manda el id del más nuevo que ya se tiene y la respuesta trae solo lo que llegó
 * después, que casi siempre es nada. Ver `listMyNotifications`.
 *
 * **El buzón es de una sesión**, igual que las reservas: se guarda con el token con que se pidió,
 * y si entra otra cuenta lo guardado no es de quien mira y se descarta. Se pide con cualquier rol
 * y no solo con CONDUCTOR —el backend contesta `isAuthenticated()`—; un operador recibe una lista
 * vacía, que es la respuesta correcta y no un error.
 */

/** Cada cuánto se vuelve a preguntar. Ver el comentario de arriba sobre por qué se puede. */
const POLL_MS = 20_000

export type NotificationsState =
  /** Sin sesión: no hay buzón que pedir. */
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'ready'; items: Notification[]; unreadCount: number }
  | { status: 'error'; message: string }

interface Snapshot {
  token: string | null
  state: NotificationsState
}

let snapshot: Snapshot = { token: null, state: { status: 'idle' } }
const listeners = new Set<() => void>()

/*
 * Los avisos que llegaron y todavía nadie mostró como recuadro flotante.
 *
 * Es una cola aparte y no una marca sobre cada aviso porque son dos preguntas distintas: "¿está
 * leído?" es del buzón y vive en el backend, y "¿ya lo anuncié?" es de esta pestaña y muere con
 * ella. Mezclarlas haría que abrir la aplicación en una pestaña nueva volviera a anunciar todo lo
 * que no se leyó.
 */
let arrivals: Notification[] = []
const arrivalListeners = new Set<() => void>()

/*
 * El pedido en curso. Uno solo a la vez: si mientras viaja alguien vuelve a pedir, la respuesta
 * que sirve es la última, y se marca con un número para descartar las anteriores.
 */
let requestNumber = 0
let pollTimer: ReturnType<typeof setInterval> | null = null

/* Cuántos componentes están mirando el buzón. Quién lo lleva y para qué: ver `useNotifications`. */
let watchers = 0

/* Cuántos componentes están mirando el buzón. Quién lo lleva y para qué: ver `useNotifications`. */
function publish(next: Snapshot): void {
  snapshot = next
  for (const listener of listeners) listener()
}

function publishArrivals(next: Notification[]): void {
  arrivals = next
  for (const listener of arrivalListeners) listener()
}

/** Los avisos que ya están a la vista, o una lista vacía si todavía no llegó ninguno. */
function currentItems(): Notification[] {
  return snapshot.state.status === 'ready' ? snapshot.state.items : []
}

/**
 * Vuelve a preguntar por el buzón de quien tiene la sesión abierta.
 *
 * La consulta es incremental: se manda el id del aviso más nuevo que ya se tiene y lo que vuelve
 * se pega adelante. Eso deja intacto lo que ya está en pantalla —incluidos los que se marcaron
 * como leídos hace un segundo— y evita que la lista parpadee cada veinte segundos.
 *
 * Mientras viaja, si ya había buzón lo deja a la vista: pasar a "cargando" haría parpadear la
 * campanita en cada consulta. El "cargando" se ve solo la primera vez.
 */
export function refreshNotifications(): void {
  const session = getSession()
  if (session === null) {
    publish({ token: null, state: { status: 'idle' } })
    return
  }

  const token = session.token
  const sameSession = snapshot.token === token
  const known = sameSession ? currentItems() : []
  const newestId = known.length > 0 ? known[0].id : undefined

  const current = requestNumber + 1
  requestNumber = current

  if (!sameSession || snapshot.state.status !== 'ready') {
    publish({ token, state: { status: 'loading' } })
  }

  listMyNotifications(newestId).then(
    (feed) => {
      if (current !== requestNumber) return

      /*
       * Con `after`, `items` son solo los nuevos y hay que pegarlos adelante; sin `after` —la
       * primera consulta de la sesión— son el buzón entero y reemplazan lo que hubiera.
       *
       * El filtro no es paranoia: la consulta incremental y una recarga completa se pueden cruzar
       * si alguien llama a esta función mientras la otra viaja, y un id repetido en una lista de
       * React es una key duplicada y una fila fantasma.
       */
      const merged =
        newestId === undefined
          ? feed.items
          : [...feed.items, ...known.filter((item) => !feed.items.some((it) => it.id === item.id))]

      publish({ token, state: { status: 'ready', items: merged, unreadCount: feed.unreadCount } })

      /*
       * Solo se anuncia lo que llegó DESPUÉS de que la pantalla ya tenía buzón. La primera
       * consulta de la sesión trae el historial entero, y anunciarlo sería recibir de golpe cinco
       * recuadros de cosas que pasaron ayer cada vez que se abre la aplicación.
       */
      if (newestId !== undefined && feed.items.length > 0) {
        publishArrivals([...feed.items, ...arrivals])
      }
    },
    () => {
      if (current !== requestNumber) return
      /*
       * Con un buzón a la vista, un fallo al refrescar no lo borra: lo que se muestra sigue siendo
       * lo último que se supo, que es mejor que un cartel de error en lugar de los avisos. Y como
       * esto se repite solo cada veinte segundos, el próximo intento ya está en camino.
       */
      if (sameSession && snapshot.state.status === 'ready') return
      publish({
        token,
        state: { status: 'error', message: 'No pudimos traer tus avisos. Probá de nuevo.' },
      })
    },
  )
}

/**
 * Marca un aviso como leído, en la pantalla primero y en el backend después.
 *
 * **Se pinta antes de preguntar** porque el gesto es de quien mira —abrió el aviso, lo tocó— y
 * esperar medio segundo a que el servidor conteste para apagar el punto se siente roto. Si la
 * llamada falla, la próxima consulta trae el estado de verdad y lo deshace: el costo de
 * equivocarse es un punto que vuelve a aparecer, no un dato perdido.
 */
export function markNotificationRead(notificationId: number): void {
  const state = snapshot.state
  if (state.status !== 'ready') return

  const target = state.items.find((item) => item.id === notificationId)
  if (target === undefined || target.read) return

  publish({
    token: snapshot.token,
    state: {
      status: 'ready',
      items: state.items.map((item) =>
        item.id === notificationId ? { ...item, read: true } : item,
      ),
      unreadCount: Math.max(0, state.unreadCount - 1),
    },
  })

  markNotificationAsRead(notificationId).catch(() => {
    /* Ver el comentario de arriba: lo arregla la próxima consulta. */
  })
}

/** Marca todo el buzón como leído. Mismo criterio optimista que `markNotificationRead`. */
export function markAllNotificationsRead(): void {
  const state = snapshot.state
  if (state.status !== 'ready' || state.unreadCount === 0) return

  publish({
    token: snapshot.token,
    state: {
      status: 'ready',
      items: state.items.map((item) => (item.read ? item : { ...item, read: true })),
      unreadCount: 0,
    },
  })

  markAllNotificationsAsRead().catch(() => {
    /* Ver `markNotificationRead`. */
  })
}

/**
 * Cuánto se espera antes de ir a buscar el aviso de algo que se acaba de hacer.
 *
 * El aviso no nace con la reserva: Reservas publica en la cola, Artemis lo entrega y
 * `ecopedia-async` recién ahí lo arma y lo guarda. Preguntar en el mismo instante en que el botón
 * contesta traería la lista sin nada, y habría que esperar igual al reloj de los veinte segundos.
 *
 * Ocho décimas es lo que tarda ese camino con todo local y sin carga, con algo de aire. Si en el
 * ambiente compartido llegara a quedar corto, no se pierde nada: el aviso aparece igual en la
 * próxima consulta.
 */
const SOON_MS = 800

/**
 * Pide el buzón enseguida, porque acaba de pasar algo que genera un aviso.
 *
 * La consulta cada veinte segundos alcanza para enterarse de lo que pasa por afuera, pero es
 * demasiado para lo que uno mismo acaba de hacer: confirmar una reserva y que el aviso aparezca
 * medio minuto después no se lee como un aviso sino como un retraso. Quien confirma o cancela
 * llama a esto y el recuadro sale casi con el gesto.
 *
 * **No reemplaza al reloj, se suma.** Si el aviso todavía no estaba listo, la consulta de siempre
 * lo trae igual: esto adelanta el caso normal, no es el único camino.
 */
export function refreshNotificationsSoon(): void {
  setTimeout(refreshNotifications, SOON_MS)
}

/** Saca un aviso de la cola de anuncios: se cerró su recuadro, o se terminó su tiempo en pantalla. */
export function dismissArrival(notificationId: number): void {
  publishArrivals(arrivals.filter((item) => item.id !== notificationId))
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function getSnapshot(): Snapshot {
  return snapshot
}

function subscribeArrivals(listener: () => void): () => void {
  arrivalListeners.add(listener)
  return () => arrivalListeners.delete(listener)
}

function getArrivals(): Notification[] {
  return arrivals
}

/**
 * El buzón de quien mira, para componentes.
 *
 * La primera vez que alguien lo pide con una sesión abierta, lo trae, y desde ahí lo vuelve a
 * pedir solo cada tanto. **El reloj es uno solo para toda la aplicación**, y no uno por
 * componente: quien use este hook se suma al que ya está andando. Se apaga cuando se va el último
 * que miraba, que es lo que evita que una pestaña en segundo plano siga consultando para nadie.
 */
export function useNotifications(): NotificationsState {
  const session = useSession()
  const current = useSyncExternalStore(subscribe, getSnapshot, getSnapshot)

  const token = session?.token ?? null

  useEffect(() => {
    if (token === null) {
      if (snapshot.token !== null) publish({ token: null, state: { status: 'idle' } })
      return
    }

    if (snapshot.token !== token) refreshNotifications()

    /*
     * El contador de suscriptores es lo que hace que el reloj sea uno solo. Sin él, la campanita y
     * la pantalla del perfil abiertas a la vez consultarían cada una por su lado, y el primer
     * `clearInterval` de los dos al desmontarse apagaría el del otro.
     */
    watchers += 1
    if (pollTimer === null) pollTimer = setInterval(refreshNotifications, POLL_MS)

    return () => {
      watchers -= 1
      if (watchers === 0 && pollTimer !== null) {
        clearInterval(pollTimer)
        pollTimer = null
      }
    }
  }, [token])

  /* Hasta que el efecto corra, un buzón de otra sesión no se muestra. */
  if (current.token !== token) return token === null ? { status: 'idle' } : { status: 'loading' }
  return current.state
}

/**
 * Los avisos que todavía no se anunciaron, para el componente que dibuja los recuadros flotantes.
 *
 * Es aparte de `useNotifications` a propósito: quien muestra la lista no quiere enterarse de esto,
 * y quien muestra los recuadros no quiere volver a dibujarse cada vez que se marca algo como
 * leído.
 */
export function useNotificationArrivals(): Notification[] {
  return useSyncExternalStore(subscribeArrivals, getArrivals, getArrivals)
}
