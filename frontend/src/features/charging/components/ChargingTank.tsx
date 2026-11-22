import type { CSSProperties, ReactNode } from 'react'

import { clampPercent, useFluidLevel } from './useFluidLevel'

interface ChargingTankProps {
  /** La batería del auto, 0–100. Null mientras el cargador no la informó: el recuadro queda vacío. */
  percent: number | null
  /** Hasta dónde se pidió cargar. Dibuja la línea punteada del límite. */
  targetPercent: number | null
  /** Cada cuánto llega un dato nuevo. El fluido tarda eso en subir, así nunca se queda quieto. */
  refreshMs: number
  children: ReactNode
  className?: string
}

/**
 * El recuadro de vidrio que se va llenando de fluido verde con la carga (RF11).
 *
 * **El fluido es solo del celular.** Ahí el recuadro es la pantalla entera y el nivel sube por toda
 * ella. Desde tablet el recuadro es solo el marco de los paneles de vidrio, y lo que se llena es la
 * batería horizontal de la escena (`ChargingBattery`): un recuadro de escritorio lleno hasta la
 * mitad de verde tapaba el auto y el cargador, que ahí sí entran.
 *
 * Cómo se mueve el nivel entre lecturas lo resuelve `useFluidLevel`, igual para los dos.
 *
 * **Se mueve con `transform` y no con `height`.** La capa del fluido mide todo el recuadro y se
 * corre hacia abajo lo que falta cargar; así la anima la placa de video y la ola de arriba
 * no se deforma, cosa que sí pasaría estirándola.
 */
export default function ChargingTank({
  percent,
  targetPercent,
  refreshMs,
  children,
  className = '',
}: ChargingTankProps) {
  const fluid = useFluidLevel(clampPercent(percent ?? 0), refreshMs)

  const levelStyle: CSSProperties = {
    transform: `translateY(${String(100 - fluid.shown)}%)`,
    transition: fluid.transition,
  }

  return (
    <section
      /*
        Sin vidrio propio en ningún tamaño: en el celular el recuadro es la pantalla entera, como el
        mapa, y desde tablet es solo el marco de los paneles, que llevan el vidrio cada uno.
      */
      className={`relative isolate overflow-hidden md:overflow-visible ${className}`}
      aria-label="Estado de la carga"
    >
      <div
        aria-hidden="true"
        className="charge-level pointer-events-none absolute inset-0 -z-10 md:hidden"
        style={levelStyle}
        onTransitionEnd={fluid.onTransitionEnd}
      >
        <Waves />
        <div className="charge-fluid absolute inset-0">
          <div className="charge-fluid-sheen absolute inset-0" />
          <Bubbles />
        </div>
      </div>

      {targetPercent !== null && <LimitLine percent={clampPercent(targetPercent)} />}

      {children}
    </section>
  )
}

/** Dos períodos de la misma onda en 1200 de ancho: correrla 600 la deja igual que al principio. */
const WAVE_PATH = 'M0 30 Q150 2 300 30 T600 30 T900 30 T1200 30 V60 H0 Z'

/**
 * La superficie del fluido: dos olas que se cruzan, apoyadas sobre el borde de arriba.
 *
 * La de atrás es más clara y va al revés, y se ve a través de la de adelante: eso da la
 * profundidad. La de adelante tiene el color de la punta de arriba del degradado, así el borde
 * entre ola y cuerpo no se ve.
 */
function Waves() {
  return (
    <div className="absolute inset-x-0 bottom-[calc(100%-1px)] h-16 overflow-hidden">
      <div className="charge-wave-bob charge-wave-bob--back absolute inset-0">
        <svg
          viewBox="0 0 1200 60"
          preserveAspectRatio="none"
          className="charge-wave charge-wave--back absolute bottom-0 left-0 h-full"
        >
          <path d={WAVE_PATH} fill="var(--fluid-back)" />
        </svg>
      </div>
      <div className="charge-wave-bob absolute inset-0">
        <svg
          viewBox="0 0 1200 60"
          preserveAspectRatio="none"
          className="charge-wave absolute bottom-0 left-0 h-3/4"
        >
          <path d={WAVE_PATH} fill="var(--fluid-top)" />
        </svg>
      </div>
    </div>
  )
}

/**
 * Burbujas fijas, no al azar: posiciones al azar cambiarían en cada render y saltarían de lugar
 * cada vez que llega una lectura.
 */
const BUBBLES = [
  { left: '8%', size: 10, duration: 9, delay: 0 },
  { left: '17%', size: 6, duration: 7, delay: 2.5 },
  { left: '29%', size: 14, duration: 11, delay: 1 },
  { left: '41%', size: 8, duration: 8, delay: 4 },
  { left: '52%', size: 12, duration: 10, delay: 6 },
  { left: '63%', size: 7, duration: 7.5, delay: 1.8 },
  { left: '74%', size: 16, duration: 12, delay: 3.2 },
  { left: '86%', size: 9, duration: 9.5, delay: 5.4 },
  { left: '93%', size: 6, duration: 8, delay: 0.7 },
]

function Bubbles() {
  return (
    <div className="absolute inset-0 overflow-hidden">
      {BUBBLES.map((bubble) => (
        <span
          key={bubble.left}
          className="charge-bubble"
          style={
            {
              left: bubble.left,
              width: bubble.size,
              height: bubble.size,
              '--bubble-duration': `${String(bubble.duration)}s`,
              '--bubble-delay': `${String(bubble.delay)}s`,
            } as CSSProperties
          }
        />
      ))}
    </div>
  )
}

/**
 * La línea punteada del límite: hasta dónde va a llegar el fluido, porque ahí la carga se corta.
 *
 * El punteado es un degradado repetido y no un `border-dashed`: el del navegador no deja elegir el
 * largo de los trazos, y en el diseño son largos y separados.
 *
 * **Va sin rótulo.** Hubo un "LÍMITE: 90%" grande arriba de la línea, pero con un objetivo alto
 * chocaba con las estadísticas. El número queda para el lector de pantalla.
 */
function LimitLine({ percent }: { percent: number }) {
  return (
    <div
      className="pointer-events-none absolute inset-x-0 z-0 h-[3px] md:hidden"
      style={{
        bottom: `${String(percent)}%`,
        background:
          'repeating-linear-gradient(90deg, var(--charge-limit-line) 0 2rem, transparent 2rem 4rem)',
      }}
    >
      <span className="sr-only">La carga se corta al {percent}%</span>
    </div>
  )
}
