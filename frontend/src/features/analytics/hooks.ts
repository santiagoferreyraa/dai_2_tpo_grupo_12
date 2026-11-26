import { useEffect, useRef, useState, type RefObject } from 'react'

/**
 * El tamaño en píxeles de un elemento, al día con cada cambio.
 *
 * Lo necesitan el gráfico y la silueta del panel: los dos se dibujan con SVG en píxeles, y un
 * `viewBox` estirado deformaría el texto de los ejes y el radio de las esquinas.
 */
export function useElementSize<T extends HTMLElement>(): [
  RefObject<T | null>,
  { width: number; height: number },
] {
  const ref = useRef<T>(null)
  const [size, setSize] = useState({ width: 0, height: 0 })

  useEffect(() => {
    const element = ref.current
    if (element === null) return
    const observer = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect
      setSize({ width: Math.round(width), height: Math.round(height) })
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  return [ref, size]
}

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

/**
 * Un número que llega a su valor contando, en vez de saltar.
 *
 * Arranca desde donde estaba, no desde cero: al cambiar de período o de selección se ve cuánto
 * subió o bajó, que es lo que dice algo. Con "reducir movimiento" pedido, salta directo.
 */
export function useCountUp(target: number, durationMs = 700): number {
  const [value, setValue] = useState(0)
  const current = useRef(0)

  const reduced = prefersReducedMotion()

  useEffect(() => {
    if (reduced) {
      current.current = target
      return
    }
    const from = current.current
    const startedAt = performance.now()
    let frame = 0
    const step = (now: number) => {
      const t = Math.min(1, (now - startedAt) / durationMs)
      const eased = 1 - (1 - t) ** 3
      current.current = from + (target - from) * eased
      setValue(current.current)
      if (t < 1) frame = requestAnimationFrame(step)
    }
    frame = requestAnimationFrame(step)
    return () => cancelAnimationFrame(frame)
  }, [target, durationMs, reduced])

  return reduced ? target : value
}
