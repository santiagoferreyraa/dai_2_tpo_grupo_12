import { formatMoney } from '@/features/bookings/charges'

import { formatDuration, formatKwh } from '../format'
import { STOP_REASON_LABEL } from '../messages'
import type { ChargingSession } from '../types'

interface SessionSummaryProps {
  session: ChargingSession
}

/**
 * Cómo terminó la carga y cuánto costó (RF11, RF14, ECO-44).
 *
 * Muestra la cuenta entera —consumo, seña descontada, total— porque es la única forma de que el
 * conductor entienda por qué se le cobró lo que se le cobró: con una reserva, la seña es el piso, y
 * un consumo chico puede no sumar nada a lo que ya pagó.
 */
export default function SessionSummary({ session }: SessionSummaryProps) {
  const durationMs =
    session.stoppedAt === null ? 0 : session.stoppedAt.getTime() - session.startedAt.getTime()
  const deposit = session.depositAmount ?? 0

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-text text-3xl leading-tight font-extrabold tracking-tight">
          {session.status === 'FAULTED' ? 'La carga se interrumpió' : 'Carga terminada'}
        </h1>
        {session.stopReason !== null && (
          <p className="text-text-muted text-sm">{STOP_REASON_LABEL[session.stopReason]}</p>
        )}
      </header>

      <dl className="flex flex-col gap-2 text-sm">
        <Row label="Energía entregada" value={`${formatKwh(session.kwhDelivered)} kWh`} />
        <Row label="Duración" value={formatDuration(durationMs)} />
        {session.batteryPercent !== null && (
          <Row
            label="Batería al terminar"
            value={`${String(Math.round(session.batteryPercent))}%`}
          />
        )}
        {session.realCost !== null && <Row label="Consumo" value={formatMoney(session.realCost)} />}
        {deposit > 0 && <Row label="Seña ya pagada" value={`− ${formatMoney(deposit)}`} />}
        {session.finalBilled !== null && (
          <Row label="Total de la carga" value={formatMoney(session.finalBilled)} strong />
        )}
      </dl>

      <p className="text-text-muted text-sm leading-relaxed" role="status">
        {paymentMessage(session)}
      </p>
    </div>
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

function Row({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="border-border/60 flex items-baseline justify-between gap-4 border-b pb-2 last:border-b-0">
      <dt className="text-text-muted">{label}</dt>
      <dd
        className={`text-text tabular-nums ${strong ? 'text-base font-extrabold' : 'font-semibold'}`}
      >
        {value}
      </dd>
    </div>
  )
}
