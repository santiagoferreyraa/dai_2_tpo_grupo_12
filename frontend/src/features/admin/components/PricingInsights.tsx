import { useId, useState } from 'react'

import type { NetworkRates } from '@/features/pricing/types'

/**
 * Lo que acompaña a los tres precios en la sección Precios: un simulador de una carga, la franja
 * de hora pico dibujada sobre el día y cómo crece la multa minuto a minuto.
 *
 * **Todo se calcula con lo que está escrito en el formulario, no con lo guardado.** La pregunta del
 * ADMIN al cambiar un precio es "¿y cuánto le sale ahora a alguien?", y la contesta antes de
 * guardar: cambia un número y el simulador y la curva se mueven.
 *
 * Las reglas —el umbral de carga rápida, la hora pico y su recargo, el escalón de la multa— son las
 * del backend (`NetworkRates`); acá no se editan, se muestran.
 */

/** Los tres precios como números, o `null` si el campo no tiene un número válido todavía. */
export interface DraftRates {
  normal: number | null
  fast: number | null
  penalty: number | null
}

const money = (value: number) => `$${value.toLocaleString('es-AR', { maximumFractionDigits: 0 })}`

const factor = (value: number) => `×${String(value).replace('.', ',')}`

/** La multa acumulada a los `minutes` minutos de exceso, con el escalón aplicado. */
function penaltyAt(minutes: number, perMinute: number, rates: NetworkRates): number {
  const base = Math.min(minutes, rates.penaltyEscalationAfterMinutes)
  const escalated = Math.max(0, minutes - rates.penaltyEscalationAfterMinutes)
  return base * perMinute + escalated * perMinute * rates.penaltyEscalationMultiplier
}

/** Un selector de dos opciones, como el de las listas de Estadísticas: pastilla que se desliza. */
function Toggle({
  label,
  options,
  value,
  onChange,
}: {
  label: string
  options: [string, string]
  value: 0 | 1
  onChange: (value: 0 | 1) => void
}) {
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className="glass-inset relative grid grid-cols-2 rounded-full p-1"
    >
      <span
        aria-hidden="true"
        className="brand-fill absolute top-1 bottom-1 left-1 w-[calc(50%-0.25rem)] rounded-full shadow-sm transition-transform duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]"
        style={{ transform: `translateX(${String(value * 100)}%)` }}
      />
      {options.map((option, index) => (
        <button
          key={option}
          type="button"
          role="radio"
          aria-checked={value === index}
          onClick={() => onChange(index as 0 | 1)}
          className={`focus-visible:outline-primary relative z-10 cursor-pointer rounded-full px-3 py-1.5 text-xs font-semibold transition-colors duration-300 focus-visible:outline-2 ${
            value === index ? 'text-on-primary' : 'text-text-muted hover:text-text'
          }`}
        >
          {option}
        </button>
      ))}
    </div>
  )
}

/** Un deslizador con su rótulo y el valor a la derecha. */
function Slider({
  label,
  value,
  min,
  max,
  step,
  unit,
  onChange,
}: {
  label: string
  value: number
  min: number
  max: number
  step: number
  unit: string
  onChange: (value: number) => void
}) {
  const id = useId()
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-baseline justify-between gap-2">
        <label htmlFor={id} className="text-text-muted text-xs font-semibold">
          {label}
        </label>
        <span className="text-text text-sm font-bold tabular-nums">
          {value} {unit}
        </span>
      </div>
      <input
        id={id}
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="accent-primary focus-visible:outline-primary w-full cursor-pointer rounded focus-visible:outline-2"
      />
    </div>
  )
}

function CostSimulator({ draft, rates }: { draft: DraftRates; rates: NetworkRates }) {
  const [kwh, setKwh] = useState(30)
  const [fast, setFast] = useState<0 | 1>(0)
  const [peak, setPeak] = useState<0 | 1>(0)
  const [excess, setExcess] = useState(0)

  const rate = fast === 1 ? draft.fast : draft.normal
  const ready = rate !== null && draft.penalty !== null
  const energy = ready ? kwh * rate * (peak === 1 ? rates.peakMultiplier : 1) : 0
  const penalty = ready ? penaltyAt(excess, draft.penalty ?? 0, rates) : 0

  return (
    <section
      aria-labelledby="pricing-sim"
      className="glass-inset analytics-rise flex flex-col gap-4 rounded-2xl p-5"
      style={{ ['--rise-delay' as string]: '120ms' }}
    >
      <div>
        <h3 id="pricing-sim" className="text-text text-base font-bold">
          Simulá una carga
        </h3>
        <p className="text-text-muted mt-0.5 text-xs">
          Con los precios del formulario, antes de guardarlos.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Toggle
          label="Tipo de carga"
          options={['Normal', 'Rápida']}
          value={fast}
          onChange={setFast}
        />
        <Toggle
          label="Horario"
          options={['Fuera de pico', 'Hora pico']}
          value={peak}
          onChange={setPeak}
        />
        <Slider
          label="Energía cargada"
          value={kwh}
          min={5}
          max={100}
          step={5}
          unit="kWh"
          onChange={setKwh}
        />
        <Slider
          label="Minutos de exceso"
          value={excess}
          min={0}
          max={60}
          step={5}
          unit="min"
          onChange={setExcess}
        />
      </div>

      {/*
        El total se anuncia cuando cambia (`aria-live`): moviendo un deslizador, el lector dice el
        número nuevo sin tener que ir a buscarlo.
      */}
      <div className="border-border/50 flex flex-wrap items-end justify-between gap-4 border-t pt-4">
        <dl className="text-text-muted flex flex-col gap-1 text-xs">
          <div className="flex gap-2">
            <dt>Energía</dt>
            <dd className="text-text font-semibold tabular-nums">
              {ready ? money(energy) : '–'}
              {peak === 1 && (
                <span className="text-text-muted ml-1">({factor(rates.peakMultiplier)} pico)</span>
              )}
            </dd>
          </div>
          <div className="flex gap-2">
            <dt>Multa</dt>
            <dd className="text-text font-semibold tabular-nums">{ready ? money(penalty) : '–'}</dd>
          </div>
        </dl>
        <p aria-live="polite" className="text-text flex items-baseline gap-2">
          <span className="text-text-muted text-xs font-semibold">Total</span>
          <span className="charge-digits text-5xl tabular-nums">
            {ready ? money(energy + penalty) : '–'}
          </span>
        </p>
      </div>
      {!ready && <p className="text-danger text-xs">Completá los tres precios para simular.</p>}
    </section>
  )
}

/** Las 24 horas del día en una tira, con la franja de hora pico encendida. */
function PeakStrip({ rates }: { rates: NetworkRates }) {
  const start = rates.peakStartHour
  const end = rates.peakEndHour
  const inPeak = (hour: number) =>
    start <= end ? hour >= start && hour < end : hour >= start || hour < end

  return (
    <section
      aria-labelledby="pricing-peak"
      className="glass-inset analytics-rise flex flex-col gap-3 rounded-2xl p-5"
      style={{ ['--rise-delay' as string]: '180ms' }}
    >
      <div className="flex items-baseline justify-between gap-3">
        <h3 id="pricing-peak" className="text-text text-base font-bold">
          Hora pico
        </h3>
        <span className="charge-digits text-text text-2xl">{factor(rates.peakMultiplier)}</span>
      </div>
      <div aria-hidden="true" className="flex h-8 gap-0.5">
        {Array.from({ length: 24 }, (_, hour) => (
          <div
            key={hour}
            className={`admin-bar flex-1 rounded-sm ${inPeak(hour) ? 'bg-st-available' : 'bg-text-muted/15'}`}
            style={{ ['--bar-delay' as string]: `${String(hour * 20)}ms` }}
          />
        ))}
      </div>
      <div
        aria-hidden="true"
        className="text-text-muted flex justify-between text-[10px] font-semibold tabular-nums"
      >
        <span>0 h</span>
        <span>6 h</span>
        <span>12 h</span>
        <span>18 h</span>
        <span>24 h</span>
      </div>
      <p className="text-text-muted text-xs">
        De {String(start)} a {String(end)} h la energía cuesta {factor(rates.peakMultiplier)}, en
        las estaciones que la tengan activada. Decide la hora en que empieza la carga.
      </p>
    </section>
  )
}

/** La multa acumulada en la primera hora de exceso, con el escalón marcado. */
function PenaltyCurve({ perMinute, rates }: { perMinute: number | null; rates: NetworkRates }) {
  const minutes = 60
  const value = perMinute ?? 0
  const max = Math.max(1, penaltyAt(minutes, value, rates))
  const W = 300
  const H = 90
  const x = (m: number) => (m / minutes) * W
  const y = (v: number) => H - (v / max) * (H - 6)
  const step = rates.penaltyEscalationAfterMinutes
  const path = [
    `M 0 ${String(H)}`,
    `L ${String(x(step))} ${String(y(penaltyAt(step, value, rates)))}`,
    `L ${String(x(minutes))} ${String(y(penaltyAt(minutes, value, rates)))}`,
  ].join(' ')

  return (
    <section
      aria-labelledby="pricing-penalty"
      className="glass-inset analytics-rise flex flex-col gap-3 rounded-2xl p-5"
      style={{ ['--rise-delay' as string]: '240ms' }}
    >
      <div className="flex items-baseline justify-between gap-3">
        <h3 id="pricing-penalty" className="text-text text-base font-bold">
          Cómo crece la multa
        </h3>
        <span className="text-text-muted text-xs">
          a la hora:{' '}
          <span className="text-text font-bold tabular-nums">
            {money(penaltyAt(minutes, value, rates))}
          </span>
        </span>
      </div>
      <svg
        viewBox={`0 0 ${String(W)} ${String(H)}`}
        className="h-24 w-full overflow-visible"
        aria-hidden="true"
      >
        <line
          x1={x(step)}
          x2={x(step)}
          y1={0}
          y2={H}
          strokeDasharray="3 4"
          className="stroke-text-muted/40"
        />
        <path d={`${path} L ${String(W)} ${String(H)} Z`} className="fill-st-available/15" />
        <path
          d={path}
          fill="none"
          strokeWidth={2.5}
          strokeLinejoin="round"
          className="stroke-st-available"
        />
      </svg>
      <p className="text-text-muted text-xs">
        Los primeros {String(step)} minutos se cobran a {perMinute === null ? '–' : money(value)}{' '}
        cada uno; desde ahí, {factor(rates.penaltyEscalationMultiplier)}.
      </p>
    </section>
  )
}

export default function PricingInsights({
  draft,
  rates,
}: {
  draft: DraftRates
  rates: NetworkRates
}) {
  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
      <CostSimulator draft={draft} rates={rates} />
      <div className="grid gap-4">
        <PeakStrip rates={rates} />
        <PenaltyCurve perMinute={draft.penalty} rates={rates} />
      </div>
    </div>
  )
}
