import { useEffect, useRef, useState } from 'react'

import { CalendarIcon } from '@/features/navigation/icons'

import { PRESETS, customPeriod, presetPeriod, toDateInput, type Period } from '../period'

interface PeriodPickerProps {
  period: Period
  onChange: (period: Period) => void
  /** El rechazo del backend al rango, si lo hubo: se dice junto a las fechas. */
  rangeError: string | null
}

const PILL = 'rounded-full px-4 py-1.5 text-sm font-semibold transition-colors duration-200'
const PILL_ON = 'brand-fill text-on-primary'
const PILL_OFF = 'glass-panel text-text hover:text-primary'

/**
 * Los tres atajos del período y el botón del rango a mano, que abre dos fechas en un vidrio.
 *
 * El rango no se aplica mientras se escribe: recién con "Aplicar". Así no sale un pedido por cada
 * día que se toca hasta llegar al que se quería.
 */
export default function PeriodPicker({ period, onChange, rangeError }: PeriodPickerProps) {
  const [open, setOpen] = useState(false)
  const [fromText, setFromText] = useState(() => toDateInput(period.from))
  const [toText, setToText] = useState(() => toDateInput(period.to))
  const [error, setError] = useState<string | null>(null)
  const boxRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent) => {
      if (boxRef.current !== null && !boxRef.current.contains(event.target as Node)) setOpen(false)
    }
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', escape)
    }
  }, [open])

  function apply() {
    const result = customPeriod(fromText, toText)
    if (typeof result === 'string') {
      setError(result)
      return
    }
    setError(null)
    setOpen(false)
    onChange(result)
  }

  return (
    <div ref={boxRef} className="relative flex items-center gap-2">
      {PRESETS.map((preset) => (
        <button
          key={preset.key}
          type="button"
          aria-pressed={period.key === preset.key}
          onClick={() => onChange(presetPeriod(preset.key))}
          className={`${PILL} ${period.key === preset.key ? PILL_ON : PILL_OFF} cursor-pointer`}
        >
          {preset.label}
        </button>
      ))}
      <button
        type="button"
        aria-label="Elegir un rango de fechas"
        aria-expanded={open}
        aria-pressed={period.key === 'custom'}
        onClick={() => {
          setFromText(toDateInput(period.from))
          setToText(toDateInput(new Date(period.to.getTime() - 1)))
          setError(null)
          setOpen((value) => !value)
        }}
        className={`grid h-9 w-9 cursor-pointer place-items-center rounded-full transition-colors duration-200 ${
          period.key === 'custom' ? PILL_ON : PILL_OFF
        }`}
      >
        <CalendarIcon className="h-4 w-4" />
      </button>

      {open && (
        <div className="glass-panel glass-modal analytics-rise absolute top-full right-0 z-[1200] mt-2 w-72 rounded-2xl p-4">
          <div className="grid grid-cols-2 gap-3">
            <label className="text-text-muted flex flex-col gap-1 text-xs font-semibold">
              Desde
              <input
                type="date"
                value={fromText}
                max={toDateInput(new Date())}
                onChange={(event) => setFromText(event.target.value)}
                className="border-border bg-background/40 text-text rounded-lg border px-2 py-1.5 text-sm"
              />
            </label>
            <label className="text-text-muted flex flex-col gap-1 text-xs font-semibold">
              Hasta
              <input
                type="date"
                value={toText}
                max={toDateInput(new Date())}
                onChange={(event) => setToText(event.target.value)}
                className="border-border bg-background/40 text-text rounded-lg border px-2 py-1.5 text-sm"
              />
            </label>
          </div>
          {(error ?? rangeError) !== null && (
            <p className="text-danger mt-2 text-xs" role="alert">
              {error ?? rangeError}
            </p>
          )}
          <button
            type="button"
            onClick={apply}
            className="brand-fill text-on-primary mt-3 w-full cursor-pointer rounded-xl py-2 text-sm font-bold"
          >
            Aplicar
          </button>
        </div>
      )}
    </div>
  )
}
