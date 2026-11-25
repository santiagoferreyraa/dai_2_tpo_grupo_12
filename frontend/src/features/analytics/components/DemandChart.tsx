import { useState, type PointerEvent } from 'react'

import { formatAmount, formatCount, formatDay, formatShortDate, formatTime } from '../format'
import { useElementSize } from '../hooks'
import { niceCeiling, type Bar, type Series } from '../series'

const AXIS_LEFT = 40
const AXIS_BOTTOM = 22
/* Deja lugar arriba para la unidad "kW", que va sobre la columna de números y no al lado del tope. */
const TOP_PAD = 20
const GRID_LINES = 4
/** La ola de entrada dura lo mismo con 96 barras que con 168: la demora por barra se reparte. */
const WAVE_MS = 450

interface DemandChartProps {
  series: Series
  from: Date
  to: Date
  /** Cambia cuando cambia lo que se mira: es lo que vuelve a disparar la entrada de las barras. */
  animationKey: string
}

/** Las marcas del eje de abajo, según cuánto tiempo abarca el gráfico. */
function ticksOf(from: Date, to: Date): { at: Date; label: string }[] {
  const span = to.getTime() - from.getTime()
  const day = 86_400_000
  const stepMs =
    span <= day * 1.5 ? 4 * 3_600_000 : span <= day * 8 ? day : span <= day * 16 ? 2 * day : 5 * day
  const label = (d: Date) =>
    stepMs < day ? formatTime(d) : stepMs === day ? formatDay(d) : formatShortDate(d)

  const first = new Date(from)
  if (stepMs < day) {
    first.setMinutes(0, 0, 0)
    first.setHours(Math.ceil(first.getHours() / 4) * 4)
  } else {
    first.setHours(24, 0, 0, 0)
  }
  const ticks = []
  for (let t = first.getTime(); t < to.getTime(); t += stepMs) {
    ticks.push({ at: new Date(t), label: label(new Date(t)) })
  }
  return ticks
}

function barRange(bar: Bar, bucketMinutes: number): string {
  if (bucketMinutes < 24 * 60) {
    const sameDay = bar.start.toDateString() === new Date().toDateString()
    const day = sameDay ? '' : `${formatDay(bar.start)} · `
    return `${day}${formatTime(bar.start)} – ${formatTime(bar.end)}`
  }
  return formatDay(bar.start)
}

/**
 * La demanda por franja: una barra por franja de 15 minutos, o por grupo de franjas cuando el
 * período es largo (ver `seriesOf`). La barra del pico va con el verde lleno de la marca.
 *
 * Se dibuja en píxeles medidos y no con un `viewBox` estirado: estirado, el texto de los ejes se
 * deforma con el ancho del panel.
 */
export default function DemandChart({ series, from, to, animationKey }: DemandChartProps) {
  const [ref, { width, height }] = useElementSize<HTMLDivElement>()
  const [hovered, setHovered] = useState<number | null>(null)

  const { bars, peakIndex, bucketMinutes } = series
  const max = niceCeiling(Math.max(...bars.map((b) => b.demandKw), 0))
  const plotWidth = Math.max(0, width - AXIS_LEFT)
  const plotHeight = Math.max(0, height - AXIS_BOTTOM - TOP_PAD)
  const slotWidth = bars.length > 0 ? plotWidth / bars.length : 0
  const gap = slotWidth > 6 ? Math.min(3, slotWidth * 0.25) : slotWidth > 2.5 ? 1 : 0
  const xOf = (date: Date) =>
    AXIS_LEFT + ((date.getTime() - from.getTime()) / (to.getTime() - from.getTime())) * plotWidth
  const hoveredBar = hovered === null ? null : (bars[hovered] ?? null)

  /** La barra bajo el puntero, o `null` si está sobre el eje de la izquierda. */
  function barAt(event: PointerEvent<HTMLDivElement>): number | null {
    if (slotWidth === 0) return null
    const x = event.clientX - event.currentTarget.getBoundingClientRect().left - AXIS_LEFT
    if (x < 0) return null
    return Math.min(bars.length - 1, Math.floor(x / slotWidth))
  }

  return (
    /*
      La barra se elige por la posición del puntero y no con un área por barra: con 168 barras de
      2 px, ni el mouse ni el dedo pueden apuntarle a una sola. Mouse y toque van por el mismo
      camino; la diferencia es que con el dedo el detalle se queda al soltar —con el mouse se va al
      salir—, porque al levantar el dedo no hay otra forma de leerlo.
    */
    <div
      ref={ref}
      className="relative min-h-0 flex-1"
      onPointerDown={(event) => setHovered(barAt(event))}
      onPointerMove={(event) => setHovered(barAt(event))}
      onPointerLeave={(event) => {
        if (event.pointerType === 'mouse') setHovered(null)
      }}
    >
      {width > 0 && height > 0 && (
        <svg
          width={width}
          height={height}
          className="absolute inset-0"
          role="img"
          aria-label="Demanda en kW por franja"
        >
          <defs>
            <linearGradient id="analytics-brand" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" style={{ stopColor: 'var(--brand-from)' }} />
              <stop offset="100%" style={{ stopColor: 'var(--brand-to)' }} />
            </linearGradient>
          </defs>

          {Array.from({ length: GRID_LINES + 1 }, (_, i) => {
            const y = TOP_PAD + (plotHeight * i) / GRID_LINES
            return (
              <g key={i}>
                <line
                  x1={AXIS_LEFT}
                  x2={width}
                  y1={y}
                  y2={y}
                  stroke="var(--color-border)"
                  strokeWidth={1}
                  strokeDasharray={i === GRID_LINES ? undefined : '3 4'}
                />
                <text
                  x={AXIS_LEFT - 8}
                  y={y + 4}
                  textAnchor="end"
                  className="fill-text-muted text-[11px]"
                >
                  {formatCount((max * (GRID_LINES - i)) / GRID_LINES)}
                </text>
              </g>
            )
          })}

          <g key={animationKey}>
            {bars.map((bar, i) => {
              const h = max > 0 ? (bar.demandKw / max) * plotHeight : 0
              if (h <= 0) return null
              const isPeak = i === peakIndex
              return (
                <rect
                  key={i}
                  className="analytics-bar"
                  style={{
                    ['--bar-delay' as string]: `${Math.round((i / bars.length) * WAVE_MS)}ms`,
                  }}
                  x={AXIS_LEFT + i * slotWidth + gap / 2}
                  y={TOP_PAD + plotHeight - h}
                  width={Math.max(1, slotWidth - gap)}
                  height={h}
                  rx={Math.min(3, (slotWidth - gap) / 2)}
                  fill={isPeak ? 'url(#analytics-brand)' : 'var(--color-primary)'}
                  fillOpacity={isPeak ? 1 : hovered === i ? 0.85 : 0.45}
                />
              )
            })}
          </g>

          {ticksOf(from, to).map((tick) => (
            <text
              key={tick.at.getTime()}
              x={xOf(tick.at)}
              y={height - 4}
              /* Las marcas pegadas a una punta se alinean hacia adentro, o el texto se corta. */
              textAnchor={
                xOf(tick.at) < AXIS_LEFT + 24
                  ? 'start'
                  : xOf(tick.at) > width - 24
                    ? 'end'
                    : 'middle'
              }
              className="fill-text-muted text-[11px]"
            >
              {tick.label}
            </text>
          ))}
          <text x={AXIS_LEFT - 8} y={10} textAnchor="end" className="fill-text-muted text-[11px]">
            kW
          </text>
        </svg>
      )}

      {hoveredBar !== null && hovered !== null && (
        <div
          className="glass-panel glass-modal pointer-events-none absolute top-0 z-10 rounded-xl px-3 py-2 text-xs whitespace-nowrap"
          style={{
            left: Math.min(
              Math.max(AXIS_LEFT + hovered * slotWidth + slotWidth / 2, 90),
              width - 90,
            ),
            transform: 'translateX(-50%)',
          }}
        >
          <p className="text-text-muted">{barRange(hoveredBar, bucketMinutes)}</p>
          <p className="text-text font-semibold">
            {formatAmount(hoveredBar.demandKw)} kW
            <span className="text-text-muted ml-2 font-normal">
              {formatAmount(hoveredBar.energyKwh)} kWh · {formatCount(hoveredBar.sessions)}{' '}
              {hoveredBar.sessions === 1 ? 'sesión' : 'sesiones'}
            </span>
          </p>
        </div>
      )}
    </div>
  )
}
