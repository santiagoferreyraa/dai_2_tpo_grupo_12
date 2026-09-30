import { useCallback, useEffect, useRef, useState } from 'react'

import { refreshNotificationsSoon } from '@/features/notifications/data/notificationsStore'
import { ApiError } from '@/lib/api'

import { confirmBooking, startHold } from './data/bookingsRepository'
import { refreshMyBookings } from './data/myBookingsStore'
import type { Booking, Hold } from './types'

/**
 * El recorrido de reservar un conector: elegir el horario, retenerlo y confirmarlo (RF08).
 *
 * Son dos pedidos y no uno porque así lo pide el backend: primero se **retiene** el slot, que
 * queda tomado diez minutos mientras el conductor revisa, y recién después se **confirma**. Este
 * hook lleva en qué paso se está y traduce cada error a algo que el conductor pueda resolver.
 *
 * No sabe nada de cómo se ve. Lo usa `BookingForm`, que es el mismo en el diálogo de escritorio
 * y en el panel del celular.
 */

export type BookingStep =
  /** Eligiendo día, hora y duración. */
  | { kind: 'choosing' }
  /** El pedido de retención está viajando. */
  | { kind: 'holding' }
  /** El slot está retenido y el conductor revisa antes de confirmar. */
  | { kind: 'reviewing'; hold: Hold }
  /** El pedido de confirmación está viajando. */
  | { kind: 'confirming'; hold: Hold }
  /** La reserva quedó guardada. */
  | { kind: 'confirmed'; booking: Booking }

export interface BookingFlow {
  step: BookingStep
  /** El problema del último intento, para mostrar en el paso en que quedó. */
  error: string | null
  requestHold: (start: Date, end: Date) => void
  confirm: () => void
  /** Vuelve a elegir el horario sin soltar la retención. Ver `requestHold`. */
  changeWindow: () => void
  /** Lo llama la cuenta regresiva cuando la retención vence sin confirmar. */
  expire: () => void
}

/**
 * Cuánto tiempo de retención tiene que quedar para reusarla en vez de pedir otra.
 *
 * Con menos, el conductor volvería a la revisión con una cuenta regresiva que vence antes de que
 * llegue a tocar Confirmar.
 */
const REUSE_MARGIN_MS = 15_000

/**
 * Las retenciones conseguidas y todavía sin confirmar, por conector.
 *
 * Hace falta porque el backend no tiene forma de soltar una retención: vence sola. Si el
 * conductor vuelve a elegir EL MISMO horario, pedir otra retención choca contra la suya propia y
 * le contesta 409, "horario tomado", sobre un horario que tomó él. Guardándola, el mismo horario
 * la reusa.
 *
 * **Vive fuera del componente a propósito.** Adentro se perdería al cerrar el diálogo, y el caso
 * de arriba pasa igual: retener, cerrar sin confirmar, volver a abrir y pedir el mismo horario.
 * Afuera dura lo que dura la pestaña, que es lo mismo que puede durar el recuerdo de quien reservó.
 */
const pendingHolds = new Map<number, Hold>()

/**
 * La retención propia todavía vigente sobre un conector, si hay una.
 *
 * La usa la disponibilidad: el backend cuenta esa retención como ocupada, pero para este conductor
 * ese mismo horario sigue siendo elegible, porque `requestHold` la reusa en vez de pedir otra.
 */
export function pendingHoldFor(connectorId: number, now: Date): Hold | null {
  const hold = pendingHolds.get(connectorId)
  if (hold === undefined || hold.expiresAt.getTime() - now.getTime() <= REUSE_MARGIN_MS) return null
  return hold
}

export function useBookingFlow(connectorId: number): BookingFlow {
  const [step, setStep] = useState<BookingStep>({ kind: 'choosing' })
  const [error, setError] = useState<string | null>(null)

  /* Si el diálogo se cierra con un pedido viajando, la respuesta no tiene a quién actualizar. */
  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const requestHold = useCallback(
    (start: Date, end: Date) => {
      const previous = pendingHolds.get(connectorId)
      if (
        previous !== undefined &&
        previous.start.getTime() === start.getTime() &&
        previous.end.getTime() === end.getTime() &&
        previous.expiresAt.getTime() - Date.now() > REUSE_MARGIN_MS
      ) {
        setError(null)
        setStep({ kind: 'reviewing', hold: previous })
        return
      }

      setError(null)
      setStep({ kind: 'holding' })

      startHold(connectorId, start, end).then(
        (hold) => {
          pendingHolds.set(connectorId, hold)
          if (!mounted.current) return
          setStep({ kind: 'reviewing', hold })
        },
        (reason: unknown) => {
          if (!mounted.current) return
          setError(holdErrorMessage(reason))
          setStep({ kind: 'choosing' })
        },
      )
    },
    [connectorId],
  )

  const confirm = useCallback(() => {
    if (step.kind !== 'reviewing') return
    const { hold } = step

    setError(null)
    setStep({ kind: 'confirming', hold })

    confirmBooking(hold.id).then(
      (booking) => {
        pendingHolds.delete(connectorId)
        /* La franja y el perfil muestran las reservas: se enteran de la nueva sin recargar. */
        refreshMyBookings()
        /*
         * Y el buzón, que en un momento va a tener el aviso de esta reserva. No se pide ya mismo:
         * el aviso pasa por la cola antes de existir. Ver `refreshNotificationsSoon`.
         */
        refreshNotificationsSoon()
        if (!mounted.current) return
        setStep({ kind: 'confirmed', booking })
      },
      (reason: unknown) => {
        if (!mounted.current) return
        const status = reason instanceof ApiError ? reason.status : null

        /*
         * Vencida, inexistente o pisada por otra reserva: la retención ya no sirve, y volver a la
         * revisión sería ofrecer confirmar algo que no se puede. Se vuelve a elegir el horario.
         * Cualquier otro error —la red, el servidor— deja la retención como estaba, y se puede
         * reintentar desde la revisión mientras no venza.
         */
        if (status === 410 || status === 404 || status === 409) {
          pendingHolds.delete(connectorId)
          setError(confirmErrorMessage(reason))
          setStep({ kind: 'choosing' })
          return
        }

        setError(confirmErrorMessage(reason))
        setStep({ kind: 'reviewing', hold })
      },
    )
  }, [step, connectorId])

  const changeWindow = useCallback(() => {
    setError(null)
    setStep({ kind: 'choosing' })
  }, [])

  const expire = useCallback(() => {
    pendingHolds.delete(connectorId)
    setError('Se venció el tiempo para confirmar. Elegí el horario de nuevo.')
    setStep({ kind: 'choosing' })
  }, [connectorId])

  return { step, error, requestHold, confirm, changeWindow, expire }
}

/*
 * ---------------------------------------------------------------------------
 * Mensajes
 *
 * El backend ya contesta en castellano, pero nombrando ids —"El conector 12 ya tiene retenida o
 * reservada una ventana que se cruza con la pedida"—, que es lo que sirve en un log y no a quien
 * reserva. Se usa su texto tal cual solo en los 400, que son los que explican una regla ("no se
 * puede reservar con más de 30 días de anticipación").
 * ---------------------------------------------------------------------------
 */

function commonErrorMessage(status: number | null): string | null {
  if (status === 0)
    return 'No se pudo conectar con el servidor. Revisá tu conexión y probá de nuevo.'
  if (status === 401 || status === 403)
    return 'Tu sesión venció. Iniciá sesión de nuevo para reservar.'
  if (status === 503) return 'No pudimos verificar el conector. Probá de nuevo en un momento.'
  return null
}

function holdErrorMessage(reason: unknown): string {
  if (!(reason instanceof ApiError)) return 'No se pudo reservar. Probá de nuevo.'
  if (reason.status === 409)
    return 'Ese horario ya no está disponible en este conector. Probá con otro.'
  if (reason.status === 404) return 'Este conector ya no existe. Elegí otro desde el mapa.'
  if (reason.status === 400) return reason.message
  return commonErrorMessage(reason.status) ?? 'No se pudo reservar. Probá de nuevo.'
}

function confirmErrorMessage(reason: unknown): string {
  if (!(reason instanceof ApiError)) return 'No se pudo confirmar. Probá de nuevo.'
  if (reason.status === 410 || reason.status === 404) {
    return 'Se venció el tiempo para confirmar. Elegí el horario de nuevo.'
  }
  if (reason.status === 409) return 'Mientras confirmabas, alguien reservó ese horario. Elegí otro.'
  return commonErrorMessage(reason.status) ?? 'No se pudo confirmar. Probá de nuevo.'
}
