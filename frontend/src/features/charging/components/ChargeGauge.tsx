import { useId, type CSSProperties, type ReactNode } from 'react'

interface ChargeGaugeProps {
  label: string
  value: ReactNode
  /** La unidad, chica debajo del número. Null si el número ya la lleva escrita ("1h 05min"). */
  unit: string | null
  /**
   * El número es largo y va bastante más chico, para que no se salga del arco ("1h 05min"). Un
   * texto de cinco caracteres ("12:05") ya se achica un poco solo.
   */
  compact?: boolean
  /**
   * - `bar`: el arco se llena con `fraction` (lo actual entre el mínimo y el máximo), y en las
   *   puntas van el mínimo y el máximo de la escala.
   * - `clock`: el arco es la esfera de un reloj: un filo fino con las marcas de los minutos y las
   *   horas hacia adentro, sin relleno ni aguja.
   */
  kind: 'bar' | 'clock'
  /** Dónde va la aguja, 0–1 (solo `bar`). Null si no hay contra qué medir: sin relleno ni aguja. */
  fraction: number | null
  /** Los extremos de la escala, solo para `bar`. */
  scale?: { min: string; max: string } | null
  /** Cuánto tarda en llegar a cada lectura nueva. Igual que la batería: así nunca va a saltos. */
  transitionMs: number
}

/** El arco abre abajo: arranca a las siete y media y termina a las cuatro y media del reloj. */
const START_DEG = 135
const SWEEP_DEG = 270
const CENTER = 100
const RADIUS = 78
/** El alto del dibujo: el arco, la aguja de afuera y los rótulos de la escala en las puntas. */
const VIEW_HEIGHT = 182

function point(deg: number, radius: number): [number, number] {
  const rad = (deg * Math.PI) / 180
  return [CENTER + radius * Math.cos(rad), CENTER + radius * Math.sin(rad)]
}

function pathPoint(deg: number, radius: number): string {
  const [x, y] = point(deg, radius)
  return `${x.toFixed(2)} ${y.toFixed(2)}`
}

/** El mismo arco para la pista y para el relleno: el relleno es la pista con un trazo cortado. */
const ARC = `M ${pathPoint(START_DEG, RADIUS)} A ${String(RADIUS)} ${String(RADIUS)} 0 1 1 ${pathPoint(
  START_DEG + SWEEP_DEG,
  RADIUS,
)}`

/**
 * El filo del reloj: un arco fino por el borde de afuera de la pista de los otros dos medidores,
 * así la esfera ocupa lo mismo que ellos.
 */
const CLOCK_RIM = RADIUS + 6
const CLOCK_ARC = `M ${pathPoint(START_DEG, CLOCK_RIM)} A ${String(CLOCK_RIM)} ${String(
  CLOCK_RIM,
)} 0 1 1 ${pathPoint(START_DEG + SWEEP_DEG, CLOCK_RIM)}`

/**
 * Las marcas de la esfera, como en un reloj de pared: salen del filo hacia el centro, una por
 * minuto —cada 6°— y la de cada hora —cada 30°— más larga y más gruesa. El arco abre abajo, así
 * que entran 45 minutos: es la esfera de un reloj al que le falta el cuarto de abajo.
 * Se calculan una vez: no dependen de nada.
 */
const CLOCK_TICKS = Array.from({ length: 46 }, (_, index) => {
  const deg = START_DEG + 6 * index
  const major = index % 5 === 0
  const [x1, y1] = point(deg, CLOCK_RIM)
  const [x2, y2] = point(deg, CLOCK_RIM - (major ? 13 : 6))
  return { x1, y1, x2, y2, major }
})

/**
 * Un medidor de arco: el de la potencia, el de la energía y el del tiempo, que es un reloj.
 *
 * **Los tres son el mismo componente a propósito.** Así el número cae en el mismo lugar y el
 * título queda a la misma altura en los tres, sin acomodar nada a mano.
 *
 * **El dibujo se mide contra el lugar que tiene, a lo alto y a lo ancho.** La pantalla de carga
 * entra entera sin scroll, así que el panel puede quedar bajo y ancho o alto y angosto; el medidor
 * toma lo que dé menos de los dos (`cqw` / `cqh`) y el número se escala con él.
 *
 * **El relleno es un trazo cortado y no un arco que se recalcula.** El camino es siempre el mismo,
 * medido en 100 (`pathLength`); lo que cambia es cuánto del trazo se pinta. Así el navegador puede
 * animarlo con una transición de CSS en vez de redibujar la curva en cada cuadro. Sube y baja con
 * la lectura: si la potencia cae, el arco baja.
 *
 * El triangulito es la aguja: va por dentro del arco, en la punta del relleno, apuntándolo. El
 * reloj no tiene: lo que avanza ahí es el número.
 */
export default function ChargeGauge({
  label,
  value,
  unit,
  compact = false,
  kind,
  fraction,
  scale = null,
  transitionMs,
}: ChargeGaugeProps) {
  const gradientId = useId()
  const filled = fraction === null ? 0 : Math.min(1, Math.max(0, fraction))

  const transition = `${String(transitionMs)}ms linear`

  const needleStyle: CSSProperties = {
    transform: `rotate(${String(START_DEG + SWEEP_DEG * filled)}deg)`,
    transformBox: 'view-box',
    transformOrigin: `${String(CENTER)}px ${String(CENTER)}px`,
    transition: `transform ${transition}`,
  }

  const [minX] = point(START_DEG, RADIUS)
  const [maxX] = point(START_DEG + SWEEP_DEG, RADIUS)

  return (
    <div className="flex h-full min-h-0 flex-col items-center">
      <dt className="sr-only">{label}</dt>
      {/* El lugar que hay: el medidor se acomoda adentro, centrado, sin pasarse de ningún lado. */}
      <dd className="relative min-h-0 w-full flex-1 [container-type:size]">
        <div
          className="absolute inset-0 m-auto [container-type:inline-size]"
          style={{
            width: `min(100cqw, calc(100cqh * ${String(200 / VIEW_HEIGHT)}))`,
            height: `min(100cqh, calc(100cqw * ${String(VIEW_HEIGHT / 200)}))`,
          }}
        >
          <svg
            viewBox={`0 0 200 ${String(VIEW_HEIGHT)}`}
            className="absolute inset-0 h-full w-full overflow-visible"
            aria-hidden="true"
          >
            <defs>
              <linearGradient id={gradientId} x1="0" y1="1" x2="1" y2="0">
                <stop offset="0%" stopColor="var(--fluid-bottom)" />
                <stop offset="100%" stopColor="var(--fluid-back)" />
              </linearGradient>
            </defs>

            {kind === 'bar' && <path d={ARC} pathLength={100} className="charge-gauge-track" />}

            {kind === 'bar'
              ? /*
                En cero no se dibuja: con las puntas redondeadas, un trazo vacío deja un punto en
                cada punta del arco. El hueco mide más que el arco por lo mismo: con un hueco de
                100 justo, el trazo siguiente asomaba como un punto en la otra punta.
              */
                filled > 0 && (
                  <path
                    d={ARC}
                    pathLength={100}
                    className="charge-gauge-fill"
                    stroke={`url(#${gradientId})`}
                    style={{
                      strokeDasharray: `${String(filled * 100)} 200`,
                      transition: `stroke-dasharray ${transition}`,
                    }}
                  />
                )
              : CLOCK_TICKS.map((tick, index) => (
                  <line
                    key={index}
                    x1={tick.x1}
                    y1={tick.y1}
                    x2={tick.x2}
                    y2={tick.y2}
                    className={tick.major ? 'charge-clock-tick--major' : 'charge-clock-tick'}
                  />
                ))}

            {/*
              El filo del reloj va después de las marcas, encima: las marcas salen del filo, y
              dibujadas arriba asomaban sus puntas a través de él.
            */}
            {kind === 'clock' && <path d={CLOCK_ARC} className="charge-clock-rim" />}

            {kind === 'bar' && fraction !== null && (
              <g style={needleStyle} className="charge-gauge-needle">
                {/* Apuntando al arco, dibujada a las tres y rotada hasta su lugar. */}
                <path d={`M ${String(CENTER + RADIUS - 11)} ${String(CENTER)} l -9 -5.5 v 11 z`} />
              </g>
            )}

            {kind === 'bar' && scale !== null && (
              <g className="charge-gauge-scale">
                <text x={minX} y={VIEW_HEIGHT - 2} textAnchor="middle">
                  {scale.min}
                </text>
                <text x={maxX} y={VIEW_HEIGHT - 2} textAnchor="middle">
                  {scale.max}
                </text>
              </g>
            )}
          </svg>

          {/*
            El número va centrado en el centro del arco y no en el de la caja: el arco abre abajo,
            así que su centro queda más abajo que la mitad del dibujo. La unidad cuelga debajo, fuera
            de la cuenta, para que lo centrado sea el número.
          */}
          <div
            className="absolute inset-x-0 flex -translate-y-1/2 justify-center"
            style={{ top: `${String((CENTER / VIEW_HEIGHT) * 100)}%` }}
          >
            <span
              className={`charge-digits text-text relative whitespace-nowrap ${
                compact
                  ? 'text-[17cqw]'
                  : typeof value === 'string' && value.length >= 5
                    ? 'text-[21cqw]'
                    : 'text-[25cqw]'
              }`}
            >
              {value}
              {unit !== null && (
                <span className="text-text-muted absolute top-full left-1/2 mt-[0.15em] charge-gauge-unit -translate-x-1/2 text-[max(10px,5.5cqw)] leading-none font-extrabold uppercase">
                  {unit}
                </span>
              )}
            </span>
          </div>
        </div>
      </dd>

      <p className="text-text mt-1 shrink-0 text-sm font-extrabold tracking-wider uppercase">
        {label}
      </p>
    </div>
  )
}
