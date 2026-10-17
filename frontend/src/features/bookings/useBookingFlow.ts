import { useCallback, useEffect, useRef, useState } from 'react'

import { refreshNotificationsSoon } from '@/features/notifications/data/notificationsStore'
import { ApiError } from '@/lib/api'

import { checkoutBooking, startHold } from './data/bookingsRepository'
import { refreshMyBookings } from './data/myBookingsStore'
import { GRACE_MINUTES } from './slots'
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

/**
 * El problema del último intento, ya en palabras del conductor: nunca un código HTTP ni el texto
 * del backend, que está escrito para el log.
 */
export interface BookingFlowError {
  message: string
  /** Falta una tarjeta: además del mensaje, se ofrece el camino para cargarla. */
  addCard: boolean
}

export interface BookingFlow {
  step: BookingStep
  /** El problema del último intento, para mostrar en el paso en que quedó. */
  error: BookingFlowError | null
  requestHold: (start: Date, end: Date) => void
  /** Recibe si el conductor aceptó la tolerancia: el backend también lo exige. */
  confirm: (acceptGracePeriod: boolean) => void
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
 * La retención conseguida y todavía sin confirmar. **Una sola, no una por conector.**
 *
 * Es una y no un mapa porque el backend tiene la misma regla: el conductor tiene una retención a
 * la vez, y pedir otra reemplaza la anterior (ver `BookingServiceImpl`). Con un mapa por conector,
 * acá quedarían recordadas retenciones que allá ya no existen, y confirmarlas daría un 404 que se
 * lee como "se te venció el tiempo" sin que se haya vencido nada.
 *
 * Para qué se recuerda: si el conductor vuelve a elegir EL MISMO horario, reusarla evita un viaje
 * al servidor y conserva la cuenta regresiva que ya venía corriendo.
 *
 * **Vive fuera del componente a propósito.** Adentro se perdería al cerrar el diálogo, y el caso
 * de arriba pasa igual: retener, cerrar sin confirmar, volver a abrir y pedir el mismo horario.
 * Afuera dura lo que dura la pestaña, que es lo mismo que puede durar el recuerdo de quien reservó.
 */
let pendingHold: Hold | null = null

/**
 * La retención propia todavía vigente sobre ese conector, si la hay.
 *
 * La usa la disponibilidad: el backend cuenta esa retención como ocupada, pero para este conductor
 * ese mismo horario sigue siendo elegible, porque `requestHold` la reusa en vez de pedir otra.
 *
 * Pregunta por conector porque quien la usa dibuja la agenda de UN conector, y la única retención
 * que hay puede ser de otro.
 */
export function pendingHoldFor(connectorId: number, now: Date): Hold | null {
  if (pendingHold === null || pendingHold.connectorId !== connectorId) return null
  if (pendingHold.expiresAt.getTime() - now.getTime() <= REUSE_MARGIN_MS) return null
  return pendingHold
}

/** Olvida la retención recordada, si es la de ese conector. */
function forgetHold(connectorId: number) {
  if (pendingHold?.connectorId === connectorId) pendingHold = null
}

export function useBookingFlow(connectorId: number): BookingFlow {
  const [step, setStep] = useState<BookingStep>({ kind: 'choosing' })
  const [error, setError] = useState<BookingFlowError | null>(null)

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
      const previous = pendingHoldFor(connectorId, new Date())
      if (
        previous !== null &&
        previous.start.getTime() === start.getTime() &&
        previous.end.getTime() === end.getTime()
      ) {
        setError(null)
        setStep({ kind: 'reviewing', hold: previous })
        return
      }

      setError(null)
      setStep({ kind: 'holding' })

      startHold(connectorId, start, end).then(
        (hold) => {
          pendingHold = hold
          if (!mounted.current) return
          setStep({ kind: 'reviewing', hold })
        },
        (reason: unknown) => {
          if (!mounted.current) return
          setError(plain(holdErrorMessage(reason)))
          setStep({ kind: 'choosing' })
        },
      )
    },
    [connectorId],
  )

  const confirm = useCallback(
    (acceptGracePeriod: boolean) => {
      if (step.kind !== 'reviewing') return
      const { hold } = step

      setError(null)
      setStep({ kind: 'confirming', hold })

      checkoutBooking(hold.id, null, acceptGracePeriod).then(
        (booking) => {
          forgetHold(connectorId)
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
           * Cualquier otro error —la red, el servidor, Pagos que no contesta, la tarjeta que falta—
           * deja la retención como estaba, y se puede reintentar desde la revisión mientras no venza.
           * Si fue la tarjeta, cargarla y volver al mismo horario reusa esta retención.
           */
          if (status === 410 || status === 404 || status === 409) {
            forgetHold(connectorId)
            setError(confirmErrorMessage(reason))
            setStep({ kind: 'choosing' })
            return
          }

          setError(confirmErrorMessage(reason))
          setStep({ kind: 'reviewing', hold })
        },
      )
    },
    [step, connectorId],
  )

  const changeWindow = useCallback(() => {
    setError(null)
    setStep({ kind: 'choosing' })
  }, [])

  const expire = useCallback(() => {
    forgetHold(connectorId)
    setError(plain('Se venció el tiempo para confirmar. Elegí el horario de nuevo.'))
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

/**
 * Los dos rechazos que son del CONDUCTOR y no del slot, que el backend marca con un `code`.
 *
 * Están acá y no en cada función porque llegan por los dos caminos —retener y confirmar— y tienen
 * que decir lo mismo en los dos. Y hacen falta porque los dos conflictos viajan como 409: sin
 * mirar el código, al conductor que ya tiene una reserva a esa hora se le contestaba "ese horario
 * ya no está disponible, probá con otro", que lo manda a buscar un conector cuando el problema no
 * es ningún conector.
 */
function driverBusyMessage(reason: ApiError): string | null {
  switch (problemCode(reason)) {
    case 'DRIVER_CHARGING':
      return 'Ya tenés una carga en curso. Vas a poder reservar de nuevo cuando termine.'
    case 'DRIVER_WINDOW_TAKEN':
      return 'Ya tenés una reserva en ese horario. Elegí otro, o cancelá la que tenés desde tu perfil.'
    default:
      return null
  }
}

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
  /* El conductor ocupado antes que el slot ocupado: los dos son 409 y solo el código los separa. */
  const busy = driverBusyMessage(reason)
  if (busy !== null) return busy
  if (reason.status === 409)
    return 'Ese horario ya no está disponible en este conector. Probá con otro.'
  if (reason.status === 404) return 'Este conector ya no existe. Elegí otro desde el mapa.'
  if (reason.status === 400) return reason.message
  return commonErrorMessage(reason.status) ?? 'No se pudo reservar. Probá de nuevo.'
}

/**
 * Los errores de confirmar. A diferencia de retener, acá ningún texto del backend llega a la
 * pantalla: los rechazos propios del checkout vienen con un `code`, y cada código tiene su frase.
 */
function confirmErrorMessage(reason: unknown): BookingFlowError {
  const retry = plain('No se pudo confirmar. Probá de nuevo.')
  if (!(reason instanceof ApiError)) return retry

  const busy = driverBusyMessage(reason)
  if (busy !== null) return plain(busy)

  switch (problemCode(reason)) {
    case 'PAYMENT_METHOD_REQUIRED':
      return {
        message: 'Para confirmar la reserva necesitás una tarjeta registrada.',
        addCard: true,
      }
    case 'PAYMENT_CHECK_UNAVAILABLE':
      return plain(
        'No pudimos verificar tu medio de pago. Esperá unos segundos y volvé a intentar: tu horario sigue guardado.',
      )
    case 'GRACE_PERIOD_NOT_ACCEPTED':
      return plain(`Para confirmar, aceptá la tolerancia de ${GRACE_MINUTES} minutos.`)
  }

  if (reason.status === 410 || reason.status === 404) {
    return plain('Se venció el tiempo para confirmar. Elegí el horario de nuevo.')
  }
  if (reason.status === 409) {
    return plain('Mientras confirmabas, alguien reservó ese horario. Elegí otro.')
  }
  const common = commonErrorMessage(reason.status)
  return common === null ? retry : plain(common)
}

/** El `code` que el checkout agrega a sus rechazos, si vino uno. */
function problemCode(reason: ApiError): string | null {
  const body = reason.detail
  if (typeof body !== 'object' || body === null || !('code' in body)) return null
  return typeof body.code === 'string' ? body.code : null
}

function plain(message: string): BookingFlowError {
  return { message, addCard: false }
}
