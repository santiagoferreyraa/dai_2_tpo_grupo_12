import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'

import { refreshMyBookings } from '@/features/bookings/data/myBookingsStore'

import ActiveSessionCard from './components/ActiveSessionCard'
import SessionSummary from './components/SessionSummary'
import StartChargingForm from './components/StartChargingForm'
import { fetchActiveSession } from './data/sessionsRepository'
import type { ChargingSession } from './types'

type PageState =
  | { kind: 'loading' }
  | { kind: 'error' }
  | { kind: 'start' }
  | { kind: 'active'; session: ChargingSession }
  | { kind: 'finished'; session: ChargingSession }

/**
 * Iniciar y detener la carga (RF11, ECO-44).
 *
 * Es una sola pantalla con tres momentos, y cuál se ve lo decide el backend, no la ruta:
 *
 * 1. **Escanear**: sin sesión activa, el conductor escanea el QR de la terminal o tipea su PIN, elige
 *    hasta qué porcentaje quiere cargar y la carga arranca. Si llegó desde la franja de su reserva,
 *    viene con `?bookingId=`; si no, el backend usa su reserva para ese conector si es la hora, y si
 *    no tiene, la carga es sin reserva (RF10).
 * 2. **Cargando**: la sesión activa, con el botón para detenerla.
 * 3. **Terminada**: lo que entregó, lo que costó y cómo se cobró.
 *
 * Al abrirla siempre se pregunta primero si hay una sesión activa: el conductor puede volver desde
 * otra pantalla, otra pestaña u otro teléfono, y ofrecerle escanear estando ya cargando lo llevaría
 * a un rechazo seguro.
 */
export default function ChargePage() {
  const [searchParams] = useSearchParams()
  const [state, setState] = useState<PageState>({ kind: 'loading' })

  const bookingParam = searchParams.get('bookingId')
  const bookingId =
    bookingParam !== null && /^\d+$/.test(bookingParam) ? Number(bookingParam) : undefined

  /* Pregunta por la sesión activa y decide qué momento mostrar. */
  const checkActive = useCallback((signal?: AbortSignal) => {
    fetchActiveSession(signal)
      .then((session) =>
        setState(session === null ? { kind: 'start' } : { kind: 'active', session }),
      )
      .catch(() => {
        if (signal?.aborted !== true) setState({ kind: 'error' })
      })
  }, [])

  /* Al abrir, el estado ya arranca en "cargando": solo falta preguntar. */
  useEffect(() => {
    const controller = new AbortController()
    checkActive(controller.signal)
    return () => controller.abort()
  }, [checkActive])

  const reload = useCallback(() => {
    setState({ kind: 'loading' })
    checkActive()
  }, [checkActive])

  /*
   * Iniciar y detener cambian el estado de la reserva (en curso, terminada): la franja de arriba y el
   * perfil la leen del mismo almacén, así que se le pide que se actualice.
   */
  const handleStarted = useCallback((session: ChargingSession) => {
    setState({ kind: 'active', session })
    refreshMyBookings()
  }, [])

  const handleFinished = useCallback((session: ChargingSession) => {
    setState({ kind: 'finished', session })
    refreshMyBookings()
  }, [])

  return (
    <div className="flex-1 overflow-y-auto">
      {/* El mismo centrado que contacto: `min-h-full` para que no se corte si no entra. */}
      <div className="flex min-h-full items-center justify-center px-4 py-8 pb-36 md:px-8 md:pb-8">
        <div className="glass-panel w-full max-w-lg rounded-3xl p-7 md:p-10">
          {state.kind === 'loading' && (
            <p className="text-text-muted text-sm" role="status">
              Buscando si tenés una carga en curso…
            </p>
          )}

          {state.kind === 'error' && (
            <div className="flex flex-col gap-4">
              <p className="text-danger text-sm" role="alert">
                No pudimos consultar tus cargas. Revisá la conexión y probá de nuevo.
              </p>
              <button
                type="button"
                onClick={reload}
                className="brand-fill text-on-primary self-start rounded-xl px-5 py-2.5 text-sm font-bold"
              >
                Reintentar
              </button>
            </div>
          )}

          {state.kind === 'start' && (
            <StartChargingForm
              bookingId={bookingId}
              onStarted={handleStarted}
              onAlreadyCharging={reload}
            />
          )}

          {state.kind === 'active' && (
            <ActiveSessionCard session={state.session} onFinished={handleFinished} />
          )}

          {state.kind === 'finished' && (
            <div className="flex flex-col gap-6">
              <SessionSummary session={state.session} />
              <Link
                to="/"
                className="brand-fill text-on-primary self-start rounded-xl px-5 py-2.5 text-sm font-bold"
              >
                Volver al inicio
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
