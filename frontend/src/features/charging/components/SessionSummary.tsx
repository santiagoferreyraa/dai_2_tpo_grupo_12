import type { ReactNode } from 'react'

import { formatMoney } from '@/features/bookings/charges'

import { formatDuration, formatKwh } from '../format'
import { STOP_REASON_LABEL } from '../messages'
import type { ChargingSession } from '../types'

interface SessionSummaryProps {
  session: ChargingSession
  /** El `id` del título, para que el diálogo que lo envuelve se nombre con él. */
  titleId?: string
  /** Lo que va en la esquina de arriba: la cruz de cerrar del diálogo. */
  corner?: ReactNode
}

const DAY = new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'short', year: 'numeric' })
const HOUR = new Intl.DateTimeFormat('es-AR', {
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/** "21 nov 2026 · 18:30": cuándo terminó, como lo imprime un ticket. */
function stampOf(date: Date): string {
  return `${DAY.format(date).replaceAll(' de ', ' ')} · ${HOUR.format(date)}`
}

/**
 * Cómo terminó la carga y cuánto costó (RF11, RF14, ECO-44), con forma de ticket.
 *
 * Muestra la cuenta entera —consumo, seña descontada, total— porque es la única forma de que el
 * conductor entienda por qué se le cobró lo que se le cobró: con una reserva, la seña es el piso, y
 * un consumo chico puede no sumar nada a lo que ya pagó.
 *
 * **Es un ticket porque es lo que es**: el comprobante de algo que se cargó y se cobró. Arriba el
 * detalle, abajo —del otro lado del troquelado— el total y cómo se pagó, que es lo que uno busca
 * primero en un ticket de verdad.
 *
 * **El troquelado está calado, no pintado.** Las muescas y el borde dentado son agujeros hechos
 * con `mask` (ver `charging.css`), así que lo que se ve por ellos es el fondo de verdad. Unos
 * círculos del color del fondo no servirían: detrás hay un oscurecido con desenfoque, que no es un
 * color que se pueda copiar.
 */
export default function SessionSummary({ session, titleId, corner }: SessionSummaryProps) {
  const durationMs =
    session.stoppedAt === null ? 0 : session.stoppedAt.getTime() - session.startedAt.getTime()
  const deposit = session.depositAmount ?? 0
  const faulted = session.status === 'FAULTED'

  return (
    <article className="charge-ticket flex flex-col">
      {/* ── El detalle ── */}
      <div className="charge-ticket__top relative px-6 pt-7 pb-6">
        {corner}

        <div className="flex flex-col items-center text-center">
          <span
            className={`flex h-14 w-14 items-center justify-center rounded-full ${
              faulted ? 'bg-danger/15 text-danger' : 'brand-fill text-on-primary'
            }`}
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
              className="h-7 w-7"
            >
              {faulted ? <path d="M12 7v6M12 17h.01" /> : <path d="m5 12.5 4.5 4.5L19 7.5" />}
            </svg>
          </span>
          <h2
            id={titleId}
            className="text-text mt-4 text-2xl leading-tight font-extrabold tracking-tight"
          >
            {faulted ? 'La carga se interrumpió' : 'Carga terminada'}
          </h2>
          {session.stopReason !== null && (
            <p className="text-text-muted mt-1 text-sm">{STOP_REASON_LABEL[session.stopReason]}</p>
          )}
          <p className="text-text-muted mt-3 text-xs font-semibold tracking-wide uppercase">
            {session.stoppedAt !== null && `${stampOf(session.stoppedAt)} · `}
            Conector {session.connectorId}
          </p>
        </div>

        <dl className="mt-6 flex flex-col gap-2.5 text-sm">
          <Row label="Energía entregada" value={`${formatKwh(session.kwhDelivered)} kWh`} />
          <Row label="Duración" value={formatDuration(durationMs)} />
          {session.batteryPercent !== null && (
            <Row
              label="Batería al terminar"
              value={`${String(Math.round(session.batteryPercent))}%`}
            />
          )}
          {session.realCost !== null && (
            <Row label="Consumo" value={formatMoney(session.realCost)} />
          )}
          {deposit > 0 && <Row label="Seña ya pagada" value={`− ${formatMoney(deposit)}`} />}
        </dl>
      </div>

      {/* El troquelado: la línea de puntos entre las dos muescas. */}
      <div aria-hidden="true" className="charge-ticket__perforation" />

      {/* ── El total ── */}
      <div className="charge-ticket__bottom px-6 pt-5">
        {session.finalBilled !== null && (
          <div className="flex items-baseline justify-between gap-4">
            <p className="text-text-muted text-sm font-semibold">Total de la carga</p>
            <p className="text-text text-3xl font-extrabold tracking-tight tabular-nums">
              {formatMoney(session.finalBilled)}
            </p>
          </div>
        )}
        <p className="text-text-muted mt-2 text-sm leading-relaxed" role="status">
          {paymentMessage(session)}
        </p>

        <Barcode seed={session.sessionId} />
      </div>
    </article>
  )
}

function paymentMessage(session: ChargingSession): string {
  const amount = session.additionalCharge ?? 0
  switch (session.paymentStatus) {
    case 'CHARGED':
      return `Cobramos ${formatMoney(amount)} a tu tarjeta.`
    case 'NOT_REQUIRED':
      return (session.depositAmount ?? 0) > 0
        ? 'La seña cubrió el consumo: no se cobró nada más.'
        : 'No hubo consumo que cobrar.'
    case 'DEBT_RECORDED':
      return `No pudimos cobrar ${formatMoney(amount)} a tu tarjeta y quedó como saldo pendiente. Hasta saldarlo no vas a poder reservar ni iniciar otra carga.`
    case 'PENDING_SETTLEMENT':
    case null:
      return 'Estamos procesando el cobro de esta carga. Lo vas a ver en tu historial.'
  }
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-text-muted">{label}</dt>
      <dd className="text-text font-semibold tabular-nums">{value}</dd>
    </div>
  )
}

/**
 * El código de barras del pie, decorativo.
 *
 * No codifica nada que se pueda escanear: es el remate visual del ticket. Los anchos salen del id
 * de la sesión, así cada ticket tiene el suyo y el mismo ticket se ve igual cada vez que se abre.
 * Debajo va el principio del id, que sí sirve para buscar la carga si algo salió mal.
 */
function Barcode({ seed }: { seed: string }) {
  const bars: { x: number; w: number }[] = []
  let x = 0
  for (let i = 0; x < 236; i++) {
    const code = seed.charCodeAt(i % seed.length) + i * 7
    const w = (code % 3) + 1
    bars.push({ x, w })
    x += w + ((code >> 2) % 3) + 1
  }

  return (
    <div aria-hidden="true" className="mt-6 flex flex-col items-center gap-1.5">
      <svg
        viewBox="0 0 240 40"
        preserveAspectRatio="none"
        className="text-text h-10 w-full opacity-80"
      >
        {bars.map((bar) => (
          <rect key={bar.x} x={bar.x} y="0" width={bar.w} height="40" fill="currentColor" />
        ))}
      </svg>
      <p className="text-text-muted font-mono text-[10px] tracking-[0.3em] uppercase">
        {seed.slice(0, 13)}
      </p>
    </div>
  )
}
