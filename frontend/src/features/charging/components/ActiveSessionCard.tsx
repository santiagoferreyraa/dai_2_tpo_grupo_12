import { useEffect, useState } from 'react'

import { useNow } from '@/features/bookings/useNow'
import { ApiError } from '@/lib/api'

import { fetchSession, stopCharging } from '../data/sessionsRepository'
import { formatDuration, formatKw, formatKwh } from '../format'
import type { ChargingSession } from '../types'

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
      onFinished(await stopCharging(session.sessionId))
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
    <div className="flex flex-col gap-6">
      <header className="flex items-center gap-3">
        <span aria-hidden="true" className="relative flex h-3 w-3">
          <span className="bg-primary absolute inline-flex h-full w-full animate-ping rounded-full opacity-60 motion-reduce:animate-none" />
          <span className="bg-primary relative inline-flex h-3 w-3 rounded-full" />
        </span>
        <h1 className="text-text text-3xl leading-tight font-extrabold tracking-tight">Cargando</h1>
      </header>

      <dl className="grid grid-cols-2 gap-4">
        <Stat label="Tiempo" value={formatDuration(elapsedMs)} />
        <Stat label="Energía" value={`${formatKwh(session.kwhDelivered)} kWh`} />
        <Stat
          label="Batería"
          value={
            session.batteryPercent === null
              ? `— / ${String(session.targetBatteryPercent)}%`
              : `${String(Math.round(session.batteryPercent))}% / ${String(session.targetBatteryPercent)}%`
          }
        />
        <Stat label="Potencia autorizada" value={`${formatKw(session.powerLimitKw)} kW`} />
      </dl>

      <p className="text-text-muted text-xs leading-relaxed">
        Conector {session.connectorId}. La carga se corta sola al llegar al{' '}
        {session.targetBatteryPercent}% o cuando se termina tu tiempo disponible. El consumo se
        cobra al terminar.
      </p>

      {error !== null && (
        <p className="text-danger text-sm" role="alert">
          {error}
        </p>
      )}

      <button
        type="button"
        onClick={() => void handleStop()}
        disabled={stopping}
        className="border-danger text-danger hover:bg-danger/10 rounded-xl border px-6 py-3 text-sm font-bold transition-colors disabled:cursor-not-allowed disabled:opacity-50"
      >
        {stopping ? 'Deteniendo…' : 'Detener carga'}
      </button>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="border-border/70 flex flex-col gap-1 rounded-2xl border px-4 py-3">
      <dt className="text-text-muted text-xs font-semibold tracking-wide uppercase">{label}</dt>
      <dd className="text-text text-xl font-extrabold tabular-nums">{value}</dd>
    </div>
  )
}
