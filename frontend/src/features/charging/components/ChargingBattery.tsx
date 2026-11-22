import type { CSSProperties } from 'react'

import { clampPercent, useFluidLevel } from './useFluidLevel'

interface ChargingBatteryProps {
  /** La batería del auto, 0–100. Null mientras el cargador no la informó: la batería queda vacía. */
  percent: number | null
  /** Hasta dónde se pidió cargar: el triangulito de arriba y la zona rayada después. */
  targetPercent: number | null
  /** Cada cuánto llega un dato nuevo. El fluido tarda eso en avanzar, así nunca se queda quieto. */
  refreshMs: number
  /** La carga sigue. Terminada, se apaga el brillo de afuera y la energía deja de correr. */
  live: boolean
}

/**
 * La batería de tablet y escritorio: una pila horizontal que se llena del mismo fluido verde que el
 * recuadro del celular, de izquierda a derecha.
 *
 * Es el mismo fluido con las mismas reglas (`useFluidLevel`): el nivel es la lectura real y avanza
 * en línea recta hasta la siguiente. Lo que cambia es la forma de la superficie: acá es el frente
 * derecho, una ola vertical que sube, con otra detrás que va al revés.
 *
 * **El límite va afuera, arriba, y no adentro.** Un triangulito que apunta al lugar exacto y el
 * número encima: adentro de la pila competía con el fluido y con las divisiones de las celdas.
 * Adentro queda solo una marca fina, y lo que está después del límite va rayado, porque es batería
 * que esta carga no va a llenar.
 */
export default function ChargingBattery({
  percent,
  targetPercent,
  refreshMs,
  live,
}: ChargingBatteryProps) {
  const fluid = useFluidLevel(clampPercent(percent ?? 0), refreshMs)
  const target = targetPercent === null ? null : clampPercent(targetPercent)

  const levelStyle: CSSProperties = {
    transform: `translateX(${String(fluid.shown - 100)}%)`,
    transition: fluid.transition,
  }

  return (
    <div className="flex h-full items-center" aria-hidden="true">
      <div
        className={`charge-battery-shell relative h-full flex-1 rounded-[1.35rem] p-1.5 ${
          live ? 'charge-battery-shell--live' : ''
        }`}
      >
        {target !== null && <TargetMarker percent={target} />}

        <div className="charge-battery-track relative isolate h-full overflow-hidden rounded-2xl">
          {target !== null && target < 100 && (
            <div
              className="charge-battery-beyond absolute inset-y-0 right-0"
              style={{ left: `${String(target)}%` }}
            />
          )}

          <div
            className="charge-level absolute inset-0"
            style={levelStyle}
            onTransitionEnd={fluid.onTransitionEnd}
          >
            <div className="charge-battery-wave charge-battery-wave--back absolute inset-0" />
            <div className="charge-battery-fluid charge-battery-wave absolute inset-0">
              <Sparks live={live} />
            </div>
          </div>

          <Cells />

          {target !== null && target < 100 && (
            <div
              className="charge-battery-target absolute inset-y-0 w-0.5 -translate-x-1/2"
              style={{ left: `${String(target)}%` }}
            />
          )}

          {/* El brillo de vidrio de la pila, por encima de todo: la hace leer como un tubo. */}
          <div className="charge-battery-gloss pointer-events-none absolute inset-0 rounded-2xl" />
        </div>
      </div>

      {/* El borne. */}
      <div className="charge-battery-cap h-[36%] w-2.5 shrink-0 rounded-r-md" />
    </div>
  )
}

/**
 * El triangulito del límite, apoyado sobre la pila y apuntando a la posición exacta, con el número
 * encima, en la condensada de las estadísticas.
 */
function TargetMarker({ percent }: { percent: number }) {
  return (
    <div className="pointer-events-none absolute inset-x-1.5 bottom-full mb-1">
      <div
        className="absolute bottom-0 flex -translate-x-1/2 flex-col items-center gap-0.5"
        style={{ left: `${String(percent)}%` }}
      >
        <span className="charge-digits text-text-muted text-base leading-none">{percent}%</span>
        <span className="charge-battery-marker h-[6px] w-[9px]" />
      </div>
    </div>
  )
}

/** Las divisiones de las celdas, cada 10%: es lo que hace que se lea como una batería. */
function Cells() {
  return (
    <div className="pointer-events-none absolute inset-0 flex">
      {Array.from({ length: 10 }, (_, index) => (
        <div key={index} className={`h-full flex-1 ${index === 0 ? '' : 'charge-battery-cell'}`} />
      ))}
    </div>
  )
}

/**
 * Chispas que corren hacia el frente: la energía entrando. Fijas y no al azar, por lo mismo que las
 * burbujas del recuadro: al azar cambiarían de lugar con cada lectura. Las recorta el fluido, así
 * que nunca pasan del nivel. Con la carga terminada se quedan quietas.
 */
const SPARKS = [
  { top: '22%', size: 5, duration: 3.4, delay: 0 },
  { top: '64%', size: 4, duration: 2.8, delay: 1.1 },
  { top: '40%', size: 7, duration: 4.2, delay: 0.5 },
  { top: '78%', size: 5, duration: 3.6, delay: 2.2 },
  { top: '30%', size: 4, duration: 2.6, delay: 1.7 },
  { top: '55%', size: 6, duration: 3.9, delay: 2.9 },
]

function Sparks({ live }: { live: boolean }) {
  if (!live) return null
  return (
    <div className="absolute inset-0 overflow-hidden [container-type:inline-size]">
      {SPARKS.map((spark) => (
        <span
          key={spark.top}
          className="charge-spark"
          style={
            {
              top: spark.top,
              width: spark.size * 3,
              height: spark.size,
              '--spark-duration': `${String(spark.duration)}s`,
              '--spark-delay': `${String(spark.delay)}s`,
            } as CSSProperties
          }
        />
      ))}
    </div>
  )
}
