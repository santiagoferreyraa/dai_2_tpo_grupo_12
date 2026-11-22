import { useEffect, useRef, useState } from 'react'

import { AGENT_BUBBLE_TOP_PX } from '@/features/agent/AgentBubble'
import { FLOAT_ABOVE_PX, type BubblePosition } from './floatingTicket'

/**
 * La tarjeta de la reserva achicada: una burbuja verde que se arrastra a donde moleste menos.
 *
 * **Existe porque la tarjeta tapa.** Flota encima del contenido, y en una lista larga o sobre un
 * rincón del mapa hay cosas que quedan debajo. Achicarla la deja a la vista sin quitarle lugar a
 * nada, y dejarla mover resuelve lo que ninguna posición fija resuelve para todas las pantallas.
 *
 * **Un toque la vuelve tarjeta; un arrastre la mueve.** Se distinguen por cuánto se movió el dedo:
 * pasado un umbral chico es arrastre, y el clic que el navegador dispara al soltar se descarta.
 * Con teclado, Enter o espacio la abren como cualquier botón.
 *
 * Adentro va la cuenta regresiva corta y un anillo con el mismo progreso que los segmentos de la
 * tarjeta, así que achicada sigue diciendo cuánto falta. El anillo es blanco en los dos temas: va
 * sobre el verde, que no cambia con el tema, así que no hay nada que adaptar.
 */

/** El diámetro de la burbuja. Del tamaño del círculo de la barra de navegación, un blanco cómodo. */
const SIZE = 60
/** Cuánto aire queda siempre entre la burbuja y los bordes de la pantalla. */
const MARGIN = 12
/** Cuánto hay que mover el dedo para que deje de ser un toque. */
const DRAG_THRESHOLD_PX = 6

interface BookingBubbleProps {
  /** La cuenta regresiva corta: "32:23", "2 h", "3 d". */
  countdown: string
  /** Para el lector de pantalla: "Tu reserva en Estación Caballito empieza en 32:23". */
  label: string
  /** De 0 a 1, el mismo progreso que los segmentos de la tarjeta. */
  progress: number
  position: BubblePosition | null
  onMove: (position: BubblePosition) => void
  onExpand: () => void
  /** La animación del tramo en curso: aparecer o explotar. Ver `BookingTicket`. */
  animationClass: string
}

/** Adentro de la pantalla, con el margen. Si la ventana se achicó, la burbuja no queda afuera. */
function clamp(position: BubblePosition): BubblePosition {
  return {
    x: Math.min(Math.max(MARGIN, position.x), window.innerWidth - SIZE - MARGIN),
    y: Math.min(Math.max(MARGIN, position.y), window.innerHeight - SIZE - MARGIN),
  }
}

/**
 * Abajo a la derecha, apenas encima de la burbuja del agente.
 *
 * La altura de la tarjeta (`FLOAT_ABOVE_PX`) ya no alcanza como punto de partida: ahí abajo está
 * ahora la burbuja del asistente, que es fija y no se puede correr. Se arranca por encima de ella
 * y el resto lo decide quien arrastre.
 */
function defaultPosition(): BubblePosition {
  const floor = Math.max(FLOAT_ABOVE_PX, AGENT_BUBBLE_TOP_PX + 12)
  return {
    x: window.innerWidth - SIZE - 16,
    y: window.innerHeight - floor - SIZE,
  }
}

export default function BookingBubble({
  countdown,
  label,
  progress,
  position,
  onMove,
  onExpand,
  animationClass,
}: BookingBubbleProps) {
  const [current, setCurrent] = useState<BubblePosition>(() => clamp(position ?? defaultPosition()))

  /* Si la ventana cambia de tamaño —girar el teléfono—, la burbuja vuelve adentro. */
  useEffect(() => {
    const onResize = () => setCurrent((previous) => clamp(previous))
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  const drag = useRef<{
    pointerId: number
    startX: number
    startY: number
    from: BubblePosition
  } | null>(null)
  const moved = useRef(false)
  /* La posición del último movimiento. Al soltar, el estado todavía puede ser la del cuadro anterior. */
  const latest = useRef(current)

  function handlePointerDown(event: React.PointerEvent<HTMLButtonElement>) {
    if (event.button !== 0) return
    drag.current = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      from: current,
    }
    moved.current = false
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  function handlePointerMove(event: React.PointerEvent<HTMLButtonElement>) {
    const state = drag.current
    if (state === null || state.pointerId !== event.pointerId) return
    const dx = event.clientX - state.startX
    const dy = event.clientY - state.startY
    if (!moved.current && Math.hypot(dx, dy) < DRAG_THRESHOLD_PX) return
    moved.current = true
    const next = clamp({ x: state.from.x + dx, y: state.from.y + dy })
    latest.current = next
    setCurrent(next)
  }

  function handlePointerUp(event: React.PointerEvent<HTMLButtonElement>) {
    const state = drag.current
    if (state === null || state.pointerId !== event.pointerId) return
    drag.current = null
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId)
    }
    /* Se guarda al soltar y no en cada movimiento: escribir el almacenamiento sesenta veces por segundo no suma nada. */
    if (moved.current) onMove(latest.current)
  }

  function handleClick() {
    /* El clic que llega después de un arrastre no abre la tarjeta: fue para moverla. */
    if (moved.current) {
      moved.current = false
      return
    }
    onExpand()
  }

  /* El anillo de progreso: un círculo con el trazo recortado según cuánto se avanzó. */
  const radius = SIZE / 2 - 3
  const circumference = 2 * Math.PI * radius

  return (
    <button
      type="button"
      aria-label={`${label}. Tocá para ver la tarjeta de la reserva.`}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerUp}
      onClick={handleClick}
      /*
        `touch-none` es lo que deja arrastrarla: sin eso, el dedo sobre la burbuja scrollea la
        página en vez de moverla. Por encima de la barra de navegación (1100), porque se puede
        soltar sobre ella; por debajo del panel que sube desde abajo (1200) y de los diálogos.
      */
      className={`booking-bubble text-on-primary ${animationClass} focus-visible:outline-primary fixed z-[1105] flex touch-none items-center justify-center rounded-full shadow-[0_10px_24px_rgb(0_0_0/0.35)] select-none focus-visible:outline-2 focus-visible:outline-offset-4 md:hidden`}
      style={{ width: SIZE, height: SIZE, left: current.x, top: current.y }}
    >
      <svg aria-hidden="true" className="absolute inset-0 -rotate-90" width={SIZE} height={SIZE}>
        <circle
          cx={SIZE / 2}
          cy={SIZE / 2}
          r={radius}
          fill="none"
          strokeWidth={3}
          style={{ stroke: '#ffffff', opacity: 0.35 }}
        />
        <circle
          cx={SIZE / 2}
          cy={SIZE / 2}
          r={radius}
          fill="none"
          strokeWidth={3}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - Math.min(1, Math.max(0, progress)))}
          style={{ stroke: '#ffffff' }}
        />
      </svg>
      <span
        aria-hidden="true"
        className="relative text-[13px] leading-none font-extrabold tracking-tight tabular-nums"
      >
        {countdown}
      </span>
    </button>
  )
}
