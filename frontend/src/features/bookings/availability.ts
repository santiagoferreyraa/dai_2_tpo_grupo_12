import { useEffect, useState } from 'react'

import { ApiError } from '@/lib/api'

import { getAvailability } from './data/bookingsRepository'
import { startOfDay, startTimesFor, windowEnd } from './slots'
import type { FreeWindow, Hold } from './types'

/**
 * Qué horarios se pueden elegir en un conector, según los huecos libres del backend (ECO-33).
 *
 * Son dos piezas: el hook que trae los huecos y las reglas que dicen si un horario entra. Las
 * reglas son funciones puras sobre los huecos, igual que `slots.ts` y `timeline.ts`.
 *
 * **La retención propia es la excepción.** El backend la cuenta como ocupada, y para cualquier otro
 * conductor lo está. Para quien la tiene, ese horario EXACTO sigue siendo elegible, porque la app
 * reusa su retención en vez de pedir otra (ver `useBookingFlow`). Un horario distinto que se cruce
 * con ella no: el backend lo rechazaría contra la retención propia igual que contra una ajena.
 */

/** Hasta dónde se pide: el horizonte entero de una vez. Un solo pedido cubre los treinta días. */
const HORIZON_DAYS = 30

export type AvailabilityState =
  | { status: 'loading' }
  | { status: 'ready'; free: FreeWindow[] }
  /**
   * El conector no se puede reservar: fuera de servicio o inexistente. No es "no se sabe": es un
   * motivo para no dejar continuar.
   */
  | { status: 'unbookable'; message: string }
  /** No se pudo consultar. El diálogo sigue andando a ciegas, como antes de ECO-33. */
  | { status: 'unknown' }

/**
 * Los huecos libres de un conector, de ahora al horizonte.
 *
 * @param refreshKey cambia cada vez que conviene volver a preguntar —tras un rechazo o un
 *   vencimiento—, porque los huecos son una foto y la foto quedó vieja.
 */
export function useAvailability(connectorId: number, refreshKey: unknown): AvailabilityState {
  const [state, setState] = useState<AvailabilityState>({ status: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    const from = new Date()
    const to = new Date(from.getTime() + HORIZON_DAYS * 24 * 60 * 60 * 1000)

    getAvailability(connectorId, from, to, controller.signal).then(
      (free) => setState({ status: 'ready', free }),
      (reason: unknown) => {
        if (controller.signal.aborted) return
        /*
          Fuera de servicio (409) o inexistente (404): la misma frase para los dos, que dice qué
          hacer y no qué le pasó al conector (Manuel, 02/11).
        */
        if (reason instanceof ApiError && (reason.status === 409 || reason.status === 404)) {
          setState({
            status: 'unbookable',
            message: 'Este conector no se puede reservar ahora. Elegí otro desde el mapa.',
          })
        } else {
          setState({ status: 'unknown' })
        }
      },
    )

    return () => controller.abort()
  }, [connectorId, refreshKey])

  return state
}

/**
 * Si una ventana que empieza en `start` y dura `minutes` entra entera en algún hueco libre, o es
 * exactamente la retención propia.
 *
 * `free` en `null` es "no se sabe" y deja pasar todo: sin datos, se elige a ciegas y el backend
 * contesta, que es como funcionaba antes.
 */
export function fits(
  free: FreeWindow[] | null,
  start: Date,
  minutes: number,
  ownHold: Hold | null,
): boolean {
  if (free === null) return true
  const end = windowEnd(start, minutes)

  if (
    ownHold !== null &&
    ownHold.start.getTime() === start.getTime() &&
    ownHold.end.getTime() === end.getTime()
  ) {
    return true
  }

  return free.some(
    (window) => window.start.getTime() <= start.getTime() && end.getTime() <= window.end.getTime(),
  )
}

/**
 * Si una hora de inicio se puede elegir: con la duración más corta, o como inicio de la retención
 * propia. Una hora donde ni media hora entra no tiene sentido ofrecerla.
 */
export function startIsAvailable(
  free: FreeWindow[] | null,
  start: Date,
  shortestMinutes: number,
  ownHold: Hold | null,
): boolean {
  if (ownHold !== null && ownHold.start.getTime() === start.getTime()) return true
  return fits(free, start, shortestMinutes, ownHold)
}

/** Si en un día queda al menos una hora de inicio elegible. */
export function dayHasRoom(
  free: FreeWindow[] | null,
  day: Date,
  now: Date,
  shortestMinutes: number,
  ownHold: Hold | null,
): boolean {
  if (free === null) return true
  return startTimesFor(startOfDay(day), now).some((start) =>
    startIsAvailable(free, start, shortestMinutes, ownHold),
  )
}
