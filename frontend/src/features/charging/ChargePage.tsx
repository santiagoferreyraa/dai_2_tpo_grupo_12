import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'

import { refreshMyBookings } from '@/features/bookings/data/myBookingsStore'

import ActiveSessionCard from './components/ActiveSessionCard'
import ChargeSummaryDialog from './components/ChargeSummaryDialog'
import ChargingStage from './components/ChargingStage'
import StartChargingForm from './components/StartChargingForm'
import { fetchActiveSession, fetchSession } from './data/sessionsRepository'
import type { ChargingSession } from './types'

/**
 * La última sesión que esta pantalla vio activa, para mostrar su resumen si terminó sin que el
 * conductor estuviera mirando.
 *
 * La carga se corta sola al llegar al objetivo, y con el simulador eso pasa en segundos. Antes, al
 * volver a esta pantalla ya no había sesión activa y se ofrecía escanear de nuevo: el conductor
 * creía que la carga se había cancelado, y si volvía a tipear el PIN arrancaba otra desde cero.
 *
 * Es solo una comodidad de este navegador: si el almacenamiento no está, se pierde el resumen,
 * no la carga. La sesión es del conductor que la inició; con otra cuenta, el backend contesta 404
 * y se descarta.
 */
const LAST_SESSION_KEY = 'ecopedia.charging.lastSessionId'

function rememberSession(sessionId: string): void {
  try {
    localStorage.setItem(LAST_SESSION_KEY, sessionId)
  } catch {
    // Sin almacenamiento no hay resumen al volver, y nada más.
  }
}

function lastSessionId(): string | null {
  try {
    return localStorage.getItem(LAST_SESSION_KEY)
  } catch {
    return null
  }
}

function forgetSession(): void {
  try {
    localStorage.removeItem(LAST_SESSION_KEY)
  } catch {
    // Idem.
  }
}

/**
 * La sesión que terminó mientras el conductor no miraba, o `null`. Ya mostrada, se olvida: el
 * resumen se ve una vez, como si hubiera estado en la pantalla cuando se cortó.
 */
async function unseenFinishedSession(signal?: AbortSignal): Promise<ChargingSession | null> {
  const sessionId = lastSessionId()
  if (sessionId === null) return null
  try {
    const session = await fetchSession(sessionId, signal)
    if (session.status === 'ACTIVE') return null
    forgetSession()
    return session
  } catch {
    if (signal?.aborted !== true) forgetSession()
    return null
  }
}

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
 * 3. **Terminada**: la misma escena, quieta, con el resumen encima en un diálogo: lo que entregó,
 *    lo que costó y cómo se cobró.
 *
 * Al abrirla siempre se pregunta primero si hay una sesión activa: el conductor puede volver desde
 * otra pantalla, otra pestaña u otro teléfono, y ofrecerle escanear estando ya cargando lo llevaría
 * a un rechazo seguro.
 */
export default function ChargePage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [state, setState] = useState<PageState>({ kind: 'loading' })

  const bookingParam = searchParams.get('bookingId')
  const bookingId =
    bookingParam !== null && /^\d+$/.test(bookingParam) ? Number(bookingParam) : undefined

  /*
   * Pregunta por la sesión activa y decide qué momento mostrar. Sin sesión activa, antes de
   * ofrecer escanear se fija si la última que vio terminó mientras el conductor estaba en otra
   * pantalla: en ese caso lo que corresponde es el resumen, no empezar otra.
   */
  const checkActive = useCallback((signal?: AbortSignal) => {
    fetchActiveSession(signal)
      .then(async (session) => {
        if (session !== null) {
          rememberSession(session.sessionId)
          setState({ kind: 'active', session })
          return
        }
        const finished = await unseenFinishedSession(signal)
        if (signal?.aborted === true) return
        setState(finished === null ? { kind: 'start' } : { kind: 'finished', session: finished })
        if (finished !== null) refreshMyBookings()
      })
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
    rememberSession(session.sessionId)
    setState({ kind: 'active', session })
    refreshMyBookings()
  }, [])

  /* El resumen ya se está viendo: no hace falta guardarlo para la vuelta. */
  const handleFinished = useCallback((session: ChargingSession) => {
    forgetSession()
    setState({ kind: 'finished', session })
    refreshMyBookings()
  }, [])

  const closeSummary = useCallback(() => {
    void navigate('/')
  }, [navigate])

  /*
    Cargando y terminada ocupan todo el ancho: son la escena del fluido, no un formulario. Terminada
    es la misma escena, quieta en el nivel al que llegó, con el resumen encima. En el celular, sin
    margen: el recuadro llega a los bordes del teléfono, como el mapa.
  */
  if (state.kind === 'active' || state.kind === 'finished') {
    return (
      <div className="flex flex-1 flex-col overflow-y-auto md:px-6 md:pt-6 md:pb-6">
        {state.kind === 'active' ? (
          <ActiveSessionCard session={state.session} onFinished={handleFinished} />
        ) : (
          <>
            <ChargingStage
              session={state.session}
              elapsedMs={
                state.session.stoppedAt === null
                  ? 0
                  : state.session.stoppedAt.getTime() - state.session.startedAt.getTime()
              }
              refreshMs={0}
              live={false}
            />
            <ChargeSummaryDialog session={state.session} onClose={closeSummary} />
          </>
        )}
      </div>
    )
  }

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
        </div>
      </div>
    </div>
  )
}
