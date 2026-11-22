import { useEffect, useState } from 'react'

import { useNow } from '@/features/bookings/useNow'
import { ApiError } from '@/lib/api'

import { fetchSession, stopCharging } from '../data/sessionsRepository'
import type { ChargingSession } from '../types'
import ChargingStage from './ChargingStage'

interface ActiveSessionCardProps {
  session: ChargingSession
  /** La sesión terminó: la detuvo el conductor, o el sistema la cortó mientras miraba. */
  onFinished: (session: ChargingSession) => void
}

/** Cada cuánto se vuelve a preguntar por la sesión mientras carga. */
const REFRESH_MS = 5_000

/**
 * La carga en curso, con el botón para detenerla (RF11, ECO-44).
 *
 * Se refresca sola cada pocos segundos: los kWh y la batería los va anotando el backend con lo que
 * informa el cargador, y la sesión puede terminar sin que el conductor toque nada —llegó al
 * porcentaje, se terminó la ventana—. Cuando eso pasa, la pantalla pasa al resumen sola.
 *
 * Detener es idempotente del lado del backend, así que un pedido cortado se puede repetir sin miedo
 * a cobrar dos veces.
 */
export default function ActiveSessionCard({
  session: initial,
  onFinished,
}: ActiveSessionCardProps) {
  const [session, setSession] = useState(initial)
  const [stopping, setStopping] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const now = useNow(1000)

  useEffect(() => {
    const controller = new AbortController()
    const timer = setInterval(() => {
      fetchSession(initial.sessionId, controller.signal)
        .then((fresh) => {
          if (fresh.status === 'ACTIVE') setSession(fresh)
          else onFinished(fresh)
        })
        .catch(() => {
          // Un refresco que falla no cambia nada: el siguiente lo vuelve a intentar.
        })
    }, REFRESH_MS)
    return () => {
      clearInterval(timer)
      controller.abort()
    }
  }, [initial.sessionId, onFinished])

  async function handleStop() {
    setStopping(true)
    setError(null)
    try {
      const stopped = await stopCharging(session.sessionId)
      /*
        La respuesta del stop trae la liquidación, pero no la batería ni el objetivo. La sesión ya
        cerrada sí tiene la batería de la última lectura del cargador; la de esta pantalla puede tener
        hasta un refresco de atraso. Si ese pedido falla, alcanza con lo último que se sabía.
      */
      const closed = await fetchSession(session.sessionId).catch(() => null)
      onFinished({
        ...stopped,
        targetBatteryPercent:
          stopped.targetBatteryPercent ??
          closed?.targetBatteryPercent ??
          session.targetBatteryPercent,
        powerLimitKw: stopped.powerLimitKw ?? closed?.powerLimitKw ?? session.powerLimitKw,
        batteryPercent: stopped.batteryPercent ?? closed?.batteryPercent ?? session.batteryPercent,
        currentPowerKw: null,
      })
    } catch (failure) {
      setError(
        failure instanceof ApiError && failure.status === 0
          ? 'No hay conexión con el servidor. Probá de nuevo: detener dos veces no cobra dos veces.'
          : 'No pudimos detener la carga. Probá de nuevo en unos segundos.',
      )
      setStopping(false)
    }
  }

  const elapsedMs = Math.max(0, now.getTime() - session.startedAt.getTime())

  return (
    <ChargingStage
      session={session}
      elapsedMs={elapsedMs}
      refreshMs={REFRESH_MS}
      live
      action={
        <>
          {error !== null && (
            <p
              className="text-danger glass-panel glass-over-photo rounded-xl px-4 py-2 text-sm"
              role="alert"
            >
              {error}
            </p>
          )}
          <button
            type="button"
            onClick={() => void handleStop()}
            disabled={stopping}
            /*
              El contorno fino, un verde más claro que el del fluido, es lo que lo despega del fondo: con la carga alta, un botón
              verde claro sobre el verde del fondo se perdía. Por dentro lleva un filo de luz arriba,
              como el vidrio de las tarjetas.
            */
            className="brand-fill text-on-primary rounded-xl px-6 py-3.5 text-sm font-extrabold tracking-wide uppercase shadow-[0_6px_20px_rgb(0_0_0/0.25),inset_0_1px_0_rgb(255_255_255/0.45)] ring-1 ring-(--charge-button-ring) transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {stopping ? 'Deteniendo…' : 'Detener carga'}
          </button>
        </>
      }
    />
  )
}
