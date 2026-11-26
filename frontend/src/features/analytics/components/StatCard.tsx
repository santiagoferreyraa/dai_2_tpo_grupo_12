import { useCountUp } from '../hooks'

interface StatCardProps {
  label: string
  value: number
  unit?: string
  format: (value: number) => string
  /** Una línea debajo del número: cuándo fue el pico. */
  caption?: string | null
  delayMs?: number
}

/**
 * Uno de los tres totales de la columna izquierda. El número llega contando (`useCountUp`).
 *
 * Es `glass-inset` y no `glass-panel`: va adentro del panel con pestaña, y dos vidrios apilados se
 * leen como uno más blanco (ver `profile.css`).
 */
export default function StatCard({
  label,
  value,
  unit,
  format,
  caption,
  delayMs = 0,
}: StatCardProps) {
  const shown = useCountUp(value)
  return (
    <div
      className="glass-inset analytics-rise flex min-h-0 flex-1 flex-col justify-center rounded-2xl px-4 py-3"
      style={{ ['--rise-delay' as string]: `${delayMs}ms` }}
    >
      <p className="text-text-muted text-xs font-semibold">{label}</p>
      <p className="text-text mt-1 text-2xl leading-tight font-bold tabular-nums xl:text-3xl">
        {format(shown)}
        {unit !== undefined && (
          <span className="text-text-muted ml-1 text-sm font-semibold">{unit}</span>
        )}
      </p>
      {caption != null && <p className="text-text-muted mt-0.5 truncate text-xs">{caption}</p>}
    </div>
  )
}
