import { useEffect, useSyncExternalStore } from 'react'

import { getSession, useSession } from '@/features/auth/session'

import type { Booking } from '../types'
import { listMyBookings } from './bookingsRepository'

/**
 * Las reservas del conductor, pedidas una vez y compartidas por quien las muestre.
 *
 * **Existe porque las muestran tres lugares a la vez**: la sección "Reservas e historial" del
 * perfil, la franja de escritorio debajo de la navegación y la tarjeta de próxima carga de la
 * portada. La franja está en todas las pantallas, así que con un pedido por componente, abrir el
 * perfil pediría la misma lista dos veces. Y peor: confirmar una reserva en el mapa dejaría a la
 * franja mostrando la lista vieja hasta recargar.
 *
 * Por eso quien cambia las reservas —confirmar, cancelar— llama a `refreshMyBookings`, y todos los
 * que las muestran se actualizan juntos.
 *
 * **La lista es de una sesión.** Se guarda con el token con que se pidió: si se cierra la sesión o
 * entra otra cuenta, lo guardado no es de quien mira y se descarta. Solo se pide con rol
 * CONDUCTOR, que es el único que tiene reservas; con otro rol el backend contestaría 404.
 */

export type MyBookingsState =
  /** Sin sesión de conductor: no hay nada que pedir. */
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'ready'; bookings: Booking[] }
  | { status: 'error'; message: string }

interface Snapshot {
  token: string | null
  state: MyBookingsState
}

let snapshot: Snapshot = { token: null, state: { status: 'idle' } }
const listeners = new Set<() => void>()

/*
 * El pedido en curso. Uno solo a la vez: si mientras viaja alguien vuelve a pedir, la respuesta
 * que sirve es la última, y se marca con un número para descartar las anteriores.
 */
let requestNumber = 0

function publish(next: Snapshot): void {
  snapshot = next
  for (const listener of listeners) listener()
}

/**
 * Vuelve a pedir las reservas de quien tiene la sesión abierta.
 *
 * Mientras viaja, si ya había una lista la deja a la vista: pasar a "cargando" haría parpadear la
 * franja y el perfil cada vez que se confirma o se cancela algo. El "cargando" se ve solo la
 * primera vez.
 */
export function refreshMyBookings(): void {
  const session = getSession()
  if (session === null || session.role !== 'CONDUCTOR') {
    publish({ token: null, state: { status: 'idle' } })
    return
  }

  const token = session.token
  const current = requestNumber + 1
  requestNumber = current

  const keepVisible = snapshot.token === token && snapshot.state.status === 'ready'
  if (!keepVisible) publish({ token, state: { status: 'loading' } })

  listMyBookings().then(
    (bookings) => {
      if (current !== requestNumber) return
      publish({ token, state: { status: 'ready', bookings } })
    },
    () => {
      if (current !== requestNumber) return
      /*
        Con una lista a la vista, un fallo al refrescar no la borra: lo que se muestra sigue siendo
        lo último que se supo, que es mejor que un cartel de error en lugar de las reservas.
      */
      if (keepVisible) return
      publish({
        token,
        state: { status: 'error', message: 'No pudimos traer tus reservas. Probá de nuevo.' },
      })
    },
  )
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function getSnapshot(): Snapshot {
  return snapshot
}

/**
 * Las reservas de quien mira, para componentes.
 *
 * La primera vez que alguien las pide con una sesión de conductor, las trae. Si la sesión cambia,
 * las vuelve a traer. Todos los componentes que lo usan leen la misma lista.
 */
export function useMyBookings(): MyBookingsState {
  const session = useSession()
  const current = useSyncExternalStore(subscribe, getSnapshot, getSnapshot)

  const token = session?.role === 'CONDUCTOR' ? session.token : null

  useEffect(() => {
    if (token === null) {
      if (snapshot.token !== null) publish({ token: null, state: { status: 'idle' } })
      return
    }
    if (snapshot.token !== token) refreshMyBookings()
  }, [token])

  /* Hasta que el efecto corra, una lista de otra sesión no se muestra. */
  if (current.token !== token) return token === null ? { status: 'idle' } : { status: 'loading' }
  return current.state
}
