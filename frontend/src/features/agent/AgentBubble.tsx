import { useEffect, useRef, useState, type PointerEvent } from 'react'

import { NAV_GEOMETRY } from '@/features/navigation/notchGeometry'
import { SparkIcon } from './icons'

/**
 * La burbuja: el único acceso al agente, flotando sobre la pantalla.
 *
 * **Va llena del verde de la marca y no tiene forma fija.** El verde la anuncia como la acción
 * principal de la pantalla —es el mismo degradado de los botones importantes, así que cambia sola
 * con el tema— y la silueta se deforma —ver `.agent-blob` en `agent.css`—, que es lo que la separa
 * de cualquier botón redondo de la interfaz y le da el aire de "algo vivo" que se espera de un
 * asistente. Lo que va encima del verde va BLANCO, que es lo que se lee sobre las dos puntas del
 * par.
 *
 * **Se arrastra, y al soltarla se pega al costado más cercano** (pedido del 27/11). Fija en la
 * esquina tapaba justo lo que hubiera ahí —un monto, una fila, un botón—; suelta en cualquier lado
 * tapaba el medio de la pantalla. Así queda siempre contra el borde izquierdo o el derecho, a la
 * altura que el usuario eligió, y se desliza suave hasta ahí al soltarla. Nunca sube por encima de
 * la barra de navegación ni, en el celular, baja hasta la barra de abajo.
 *
 * Un toque sin arrastrar la abre: recién a partir de unos píxeles de movimiento el gesto cuenta
 * como arrastre, y ese soltar ya no abre el chat. Se guarda de qué lado quedó y a qué altura, así
 * que si la ventana cambia de ancho sigue pegada a su borde. Sin nada guardado va en la esquina de
 * abajo a la derecha, como siempre.
 *
 * **Con el chat abierto se desvanece**, en el celular y en la PC: el chat tiene su propio botón de
 * cerrar, y al cerrarlo la burbuja vuelve a aparecer donde se la dejó.
 */

/** El diámetro. Más grande que el círculo de la barra de navegación: es un blanco, no un ícono. */
const SIZE = 64

/**
 * A qué altura flota en el celular: la barra de navegación, más el círculo que sobresale de
 * ella, más aire. Sale de las medidas que dibujan la barra, así que si la barra cambia, la
 * burbuja la sigue en vez de quedar pisándola. En pantalla ancha no hay barra abajo y alcanza con
 * el margen de siempre (`md:bottom-6`).
 */
const MOBILE_BOTTOM_PX =
  NAV_GEOMETRY.barHeight + NAV_GEOMETRY.puckSize / 2 + NAV_GEOMETRY.notchLift + 16

/**
 * Dónde termina la burbuja por arriba en el celular, en su lugar de siempre: lo que necesita lo
 * que flota encima de ella (la burbuja de la reserva) para no pisarla.
 */
export const AGENT_BUBBLE_TOP_PX = MOBILE_BOTTOM_PX + SIZE

/** Cuánto hay que mover el puntero para que el gesto sea arrastrar y no tocar. */
const DRAG_THRESHOLD_PX = 6

/** La franja de navegación de arriba en pantalla ancha (`md:pt-20` del `<main>`), más aire. */
const DESKTOP_TOP_PX = 80 + 8

const STORAGE_KEY = 'ecopedia.agentBubble'

type Side = 'left' | 'right'

/** Dónde quedó: contra qué costado y a qué altura. */
interface Dock {
  side: Side
  y: number
}

const isWide = () => window.innerWidth >= 768

/** El margen contra los costados: el mismo de la esquina de siempre (`right-4` / `md:right-6`). */
const sideMargin = () => (isWide() ? 24 : 16)

/** Entre qué alturas puede estar: debajo de la barra de arriba y encima de la de abajo. */
function verticalRange(): [number, number] {
  const top = isWide() ? DESKTOP_TOP_PX : 16
  const bottom = window.innerHeight - SIZE - (isWide() ? 24 : MOBILE_BOTTOM_PX)
  return [top, Math.max(top, bottom)]
}

function clampY(y: number): number {
  const [min, max] = verticalRange()
  return Math.min(Math.max(min, y), max)
}

/** La `x` de un costado. */
function xOf(side: Side): number {
  return side === 'left' ? sideMargin() : window.innerWidth - SIZE - sideMargin()
}

function readStored(): Dock | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (raw === null) return null
    const value = JSON.parse(raw) as Partial<Dock>
    if ((value.side !== 'left' && value.side !== 'right') || typeof value.y !== 'number') {
      return null
    }
    return { side: value.side, y: clampY(value.y) }
  } catch {
    return null
  }
}

function store(dock: Dock): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(dock))
  } catch {
    // Sin almacenamiento la burbuja se arrastra igual; solo no recuerda el lugar al volver.
  }
}

interface AgentBubbleProps {
  open: boolean
  onToggle: () => void
  ref?: React.Ref<HTMLButtonElement>
}

export default function AgentBubble({ open, onToggle, ref }: AgentBubbleProps) {
  const [dock, setDock] = useState<Dock | null>(readStored)
  /* Mientras se arrastra, la posición libre que sigue al puntero; `null` cuando está pegada. */
  const [dragAt, setDragAt] = useState<{ x: number; y: number } | null>(null)
  /* Lo que mide la ventana: cambia la `x` del costado derecho y los topes de la altura. */
  const [, setViewport] = useState(0)
  const drag = useRef<{ startX: number; startY: number; originX: number; originY: number } | null>(
    null,
  )
  const moved = useRef(false)

  useEffect(() => {
    const onResize = () => {
      setViewport(window.innerWidth * 10_000 + window.innerHeight)
      setDock((current) => (current === null ? null : { ...current, y: clampY(current.y) }))
    }
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  function onPointerDown(event: PointerEvent<HTMLButtonElement>) {
    if (event.button !== 0) return
    const box = event.currentTarget.getBoundingClientRect()
    drag.current = {
      startX: event.clientX,
      startY: event.clientY,
      originX: box.left,
      originY: box.top,
    }
    moved.current = false
    event.currentTarget.setPointerCapture(event.pointerId)
  }

  function onPointerMove(event: PointerEvent<HTMLButtonElement>) {
    const start = drag.current
    if (start === null) return
    const dx = event.clientX - start.startX
    const dy = event.clientY - start.startY
    if (!moved.current && Math.hypot(dx, dy) < DRAG_THRESHOLD_PX) return
    moved.current = true
    /* Mientras se arrastra va libre de costado, pero nunca por encima ni por debajo de las barras. */
    const maxX = window.innerWidth - SIZE - 8
    setDragAt({
      x: Math.min(Math.max(8, start.originX + dx), maxX),
      y: clampY(start.originY + dy),
    })
  }

  function onPointerUp() {
    if (drag.current === null) return
    drag.current = null
    if (!moved.current || dragAt === null) return
    /* Se pega al costado más cercano; la transición de `left` la lleva hasta ahí. */
    const side: Side = dragAt.x + SIZE / 2 < window.innerWidth / 2 ? 'left' : 'right'
    const next = { side, y: dragAt.y }
    setDock(next)
    setDragAt(null)
    store(next)
  }

  const placed = dragAt !== null ? dragAt : dock !== null ? { x: xOf(dock.side), y: dock.y } : null

  return (
    <button
      ref={ref}
      type="button"
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
      onClick={() => {
        /* El soltar de un arrastre también dispara el clic: ese no abre el chat. */
        if (moved.current) {
          moved.current = false
          return
        }
        onToggle()
      }}
      aria-expanded={open}
      aria-haspopup="dialog"
      aria-label="Abrir el chat con el asistente"
      /* Con el chat abierto no se ve ni se alcanza con el Tab: el chat tiene su botón de cerrar. */
      tabIndex={open ? -1 : undefined}
      aria-hidden={open ? true : undefined}
      /*
        Por encima de la navegación (1100 y 1140) y por debajo de las ventanas modales (1200), que
        tienen que poder taparla.

        `touch-none` es lo que deja arrastrarla con el dedo: sin eso, el navegador toma el gesto
        como scroll de la página y la burbuja no se mueve.

        Mientras se arrastra, `left` y `top` no tienen transición —la burbuja sigue al puntero sin
        retraso—; al soltarla sí, y eso es lo que la desliza suave hasta su costado. La aparición y
        la desaparición, al abrir y cerrar el chat, son de opacidad y escala.
      */
      className={`agent-bubble group focus-visible:outline-primary fixed z-[1150] flex cursor-grab touch-none items-center justify-center select-none focus-visible:outline-2 focus-visible:outline-offset-4 active:cursor-grabbing ${
        dragAt !== null ? 'agent-bubble--dragging' : ''
      } ${placed === null ? 'right-4 bottom-[var(--agent-bubble-bottom)] md:right-6 md:bottom-6' : ''} ${
        open ? 'pointer-events-none scale-75 opacity-0' : 'scale-100 opacity-100 hover:scale-105'
      }`}
      style={
        {
          width: SIZE,
          height: SIZE,
          '--agent-bubble-bottom': `${MOBILE_BOTTOM_PX}px`,
          ...(placed === null ? {} : { left: placed.x, top: placed.y }),
        } as React.CSSProperties
      }
    >
      {/*
        Las dos capas de atrás son decoración pura y no anuncian nada. Van con `inset` negativo el
        halo —tiene que asomar por afuera— y a ras el cuerpo.
      */}
      <span aria-hidden="true" className="agent-blob-halo absolute -inset-2 rounded-full" />
      <span aria-hidden="true" className="agent-blob absolute inset-0" />

      {/* El ícono NO gira con la gota: es hijo del botón, no de la capa que rota. */}
      <span
        aria-hidden="true"
        className="relative grid place-items-center transition-transform duration-200 group-hover:scale-110 motion-reduce:transition-none"
      >
        <SparkIcon className="h-7 w-7 text-white" />
      </span>
    </button>
  )
}
