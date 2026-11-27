import type { ReactNode } from 'react'

/**
 * Los gráficos chicos del tablero del backoffice: las barritas de las tarjetas, la dona de roles y
 * el medidor de conectores.
 *
 * Son SVG y `div` a mano y no una librería, igual que el gráfico de Estadísticas: son tres figuras
 * simples, y dibujarlas acá deja que tomen los colores del tema y que entren con las mismas curvas
 * que el resto. Las animaciones están en `admin.css`; con "reducir movimiento" aparecen quietas.
 *
 * **Se estiran con su caja.** El tablero entra entero en la pantalla sin scrollear, así que el
 * alto de cada panel lo decide la ventana y no el contenido: la dona y el medidor ocupan lo que
 * les toque, sin un tamaño fijo que desborde en una pantalla baja.
 *
 * Ninguno es la única forma de leer el dato: cada uno va acompañado del número escrito, así que el
 * dibujo lleva `aria-hidden` y el lector de pantalla lee el texto de al lado.
 */

/**
 * Barritas verticales. Por omisión las que tienen dato —y la última, aunque esté en cero— van en el
 * verde de los conectores disponibles, el mismo de todas las barras del backoffice, y las vacías en
 * gris; con
 * `colors`, cada una lleva el suyo (los roles de los usuarios, el estado de cada estación).
 *
 * Son `div` y no SVG: el alto de cada una es un porcentaje de la caja, y un SVG estirado dentro de
 * un flex sin alto definido toma su tamaño intrínseco y se sale de la tarjeta.
 */
export function MiniBars({
  values,
  colors,
  highlight = values.length - 1,
}: {
  values: number[]
  /** Un estilo de fondo por barra. Si falta, la resaltada va en verde y el resto en gris. */
  colors?: string[]
  highlight?: number
}) {
  const max = Math.max(...values, 0)
  return (
    <div
      aria-hidden="true"
      /* Muchas barras (una por estación) van más juntas, para no salirse de una tarjeta angosta. */
      className={`flex h-full items-end overflow-hidden ${values.length > 8 ? 'gap-0.5' : 'gap-1'}`}
    >
      {values.map((value, index) => {
        /* Sin datos, un piso parejo: barras de alto cero no se ven y el recuadro queda vacío. */
        const height = max === 0 ? 12 : Math.max(12, (value / max) * 100)
        const color = colors?.[index]
        return (
          <div key={index} className="relative h-full max-w-4 min-w-0 flex-1">
            <div
              className={`admin-bar absolute inset-x-0 bottom-0 rounded-sm ${
                color !== undefined
                  ? ''
                  : value > 0 || index === highlight
                    ? 'bg-st-available'
                    : 'bg-text-muted/25'
              }`}
              style={{
                height: `${String(height)}%`,
                background: color,
                ['--bar-delay' as string]: `${String(index * 40)}ms`,
              }}
            />
          </div>
        )
      })}
    </div>
  )
}

export interface DonutSlice {
  label: string
  value: number
  /** El color del tramo, como variable de CSS (cambia con el tema). */
  color: string
}

const DONUT_RADIUS = 42
const DONUT_LENGTH = 2 * Math.PI * DONUT_RADIUS

/**
 * La dona: un anillo por tramo, cada uno arrancando donde terminó el anterior, con un hueco fino
 * entre tramos y las puntas rectas.
 */
export function Donut({
  slices,
  center,
  caption,
}: {
  slices: DonutSlice[]
  center: string
  caption: string
}) {
  const total = slices.reduce((sum, slice) => sum + slice.value, 0)
  /* El hueco entre tramos, en unidades del trazo. Con un solo tramo no hay entre quién. */
  const gap = slices.filter((s) => s.value > 0).length > 1 ? 2 : 0
  let offset = 0

  return (
    <div className="relative h-full min-h-0 w-full">
      <svg viewBox="0 0 100 100" className="absolute inset-0 h-full w-full" aria-hidden="true">
        <g transform="rotate(-90 50 50)">
          <circle
            cx="50"
            cy="50"
            r={DONUT_RADIUS}
            fill="none"
            strokeWidth="10"
            className="stroke-text-muted/15"
          />
          {total > 0 &&
            slices.map((slice, index) => {
              const length = (slice.value / total) * DONUT_LENGTH
              const drawn = Math.max(0, length - gap)
              const start = offset
              offset += length
              if (drawn === 0) return null
              return (
                <circle
                  key={slice.label}
                  cx="50"
                  cy="50"
                  r={DONUT_RADIUS}
                  fill="none"
                  strokeWidth="10"
                  stroke={slice.color}
                  className="admin-arc"
                  strokeDasharray={`${String(drawn)} ${String(DONUT_LENGTH)}`}
                  strokeDashoffset={-start}
                  style={{
                    ['--arc-length' as string]: String(DONUT_LENGTH),
                    ['--arc-delay' as string]: `${String(120 + index * 140)}ms`,
                  }}
                />
              )
            })}
        </g>
      </svg>
      {/*
        El centro se mide contra la dona (`cqmin`) y no en píxeles fijos: la dona crece y se
        achica con la ventana, y un número de tamaño fijo se montaba sobre el anillo cuando le
        tocaba poco alto.
      */}
      <div className="absolute inset-0 flex flex-col items-center justify-center [container-type:size]">
        <span className="charge-digits text-text text-[28cqmin] leading-none">{center}</span>
        <span className="text-text-muted mt-[2cqmin] text-[min(11px,9cqmin)] font-semibold">
          {caption}
        </span>
      </div>
    </div>
  )
}

export interface GaugeSegment {
  value: number
  /** Clase de trazo de las rayitas de este tramo. */
  stroke: string
}

const GAUGE_TICKS = 31

/**
 * El medidor: un semicírculo de rayitas, coloreadas de izquierda a derecha según cuánto pesa cada
 * tramo. Es el "Speed Statistic" de la referencia, contando conectores en vez de velocidad.
 */
export function Gauge({ segments, children }: { segments: GaugeSegment[]; children: ReactNode }) {
  const total = segments.reduce((sum, s) => sum + s.value, 0)
  /* El tramo de cada rayita: se reparten en proporción y el redondeo lo absorbe el último. */
  const owner: (string | null)[] = []
  if (total > 0) {
    let assigned = 0
    segments.forEach((segment, index) => {
      const count =
        index === segments.length - 1
          ? GAUGE_TICKS - assigned
          : Math.round((segment.value / total) * GAUGE_TICKS)
      for (let i = 0; i < count && owner.length < GAUGE_TICKS; i++) owner.push(segment.stroke)
      assigned += count
    })
  }

  return (
    <div className="relative h-full min-h-0 w-full">
      <svg
        viewBox="0 0 200 108"
        preserveAspectRatio="xMidYMax meet"
        className="absolute inset-0 h-full w-full"
        aria-hidden="true"
      >
        {Array.from({ length: GAUGE_TICKS }, (_, i) => {
          const angle = Math.PI - (i / (GAUGE_TICKS - 1)) * Math.PI
          const cos = Math.cos(angle)
          const sin = Math.sin(angle)
          return (
            <line
              key={i}
              x1={100 + cos * 74}
              y1={102 - sin * 74}
              x2={100 + cos * 94}
              y2={102 - sin * 94}
              strokeWidth="6"
              strokeLinecap="round"
              className={`admin-tick ${owner[i] ?? 'stroke-text-muted/20'}`}
              style={{ ['--tick-delay' as string]: `${String(150 + i * 22)}ms` }}
            />
          )
        })}
      </svg>
      <div className="absolute inset-x-0 bottom-0 flex flex-col items-center">{children}</div>
    </div>
  )
}
