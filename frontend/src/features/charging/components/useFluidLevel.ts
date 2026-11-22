import { useEffect, useRef, useState } from 'react'

/** La batería, entre 0 y 100: el cargador puede mandar algo fuera de rango y el fluido no se sale. */
export function clampPercent(value: number): number {
  return Math.min(100, Math.max(0, value))
}

interface FluidLevel {
  /** El nivel que se dibuja ahora: arranca en cero y pasa al real en el cuadro siguiente. */
  shown: number
  /** La transición del `transform` del fluido, según sea el primer llenado o una lectura nueva. */
  transition: string
  /** Va en el `onTransitionEnd` de la capa que se mueve: termina el primer llenado. */
  onTransitionEnd: () => void
}

/**
 * Cómo se mueve el fluido entre dos lecturas de la batería. Lo usan el recuadro del celular, que se
 * llena hacia arriba, y la batería de escritorio, que se llena hacia la derecha.
 *
 * **El nivel es la batería real, no una animación decorativa.** Lo único que se inventa es el
 * camino entre dos lecturas.
 *
 * **Sube en línea recta durante exactamente un intervalo de refresco.** Las lecturas llegan cada
 * pocos segundos; si el fluido llegara rápido y esperara, la carga se vería a saltos. Tardando lo
 * mismo que tarda el dato siguiente, cuando llega la próxima lectura todavía se está moviendo y la
 * subida se lee continua. La primera vez es distinta: arranca vacío y se llena hasta donde está la
 * batería con una curva que frena al final, que es lo que pide la vista al abrir la pantalla.
 *
 * Lo que se dibuja arranca en cero y pasa al nivel real en el cuadro siguiente: si el primer render
 * ya trajera el nivel, el navegador no tendría desde dónde animar y aparecería lleno de golpe.
 */
export function useFluidLevel(level: number, refreshMs: number): FluidLevel {
  const [shown, setShown] = useState(0)
  const filledOnce = useRef(false)
  const [firstFill, setFirstFill] = useState(true)

  useEffect(() => {
    const frame = requestAnimationFrame(() => setShown(level))
    return () => cancelAnimationFrame(frame)
  }, [level])

  return {
    shown,
    transition: firstFill
      ? 'transform 1800ms cubic-bezier(0.22, 1, 0.36, 1)'
      : `transform ${String(refreshMs)}ms linear`,
    onTransitionEnd: () => {
      if (!filledOnce.current && shown > 0) {
        filledOnce.current = true
        setFirstFill(false)
      }
    },
  }
}
