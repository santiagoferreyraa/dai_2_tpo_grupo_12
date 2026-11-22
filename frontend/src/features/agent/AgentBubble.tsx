import { NAV_GEOMETRY } from '@/features/navigation/notchGeometry'
import { CrossIcon } from '@/features/navigation/icons'
import { SparkIcon } from './icons'

/**
 * La burbuja: el único acceso al agente, flotando en la esquina de abajo a la derecha.
 *
 * **Va llena del verde de la marca y no tiene forma fija.** El verde la anuncia como la acción
 * principal de la pantalla —es el mismo degradado de los botones importantes, así que cambia sola
 * con el tema— y la silueta se deforma —ver `.agent-blob` en `agent.css`—, que es lo que la separa
 * de cualquier botón redondo de la interfaz y le da el aire de "algo vivo" que se espera de un
 * asistente. Lo que va encima del verde va BLANCO, que es lo que se lee sobre las dos puntas del
 * par.
 *
 * Abierta se convierte en el botón de cerrar. Es el mismo botón, no uno nuevo: el foco no se
 * mueve de lugar cuando el chat se abre y se cierra con la tecla Escape.
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
 * Hasta dónde llega la burbuja en el celular, contado desde el borde de abajo.
 *
 * Lo exporta para la burbuja de la reserva, que flota en el mismo rincón y arranca justo encima.
 * Sin esto las dos nacen una sobre la otra —la de la reserva se puede arrastrar, pero tener que
 * moverla para descubrir qué había debajo no es un buen primer encuentro—.
 */
export const AGENT_BUBBLE_TOP_PX = MOBILE_BOTTOM_PX + SIZE

interface AgentBubbleProps {
  open: boolean
  onToggle: () => void
  ref?: React.Ref<HTMLButtonElement>
}

export default function AgentBubble({ open, onToggle, ref }: AgentBubbleProps) {
  return (
    <button
      ref={ref}
      type="button"
      onClick={onToggle}
      aria-expanded={open}
      aria-haspopup="dialog"
      aria-label={open ? 'Cerrar el chat del asistente' : 'Abrir el chat con el asistente'}
      /*
        Por encima de la navegación (1100 y 1140) y por debajo de las ventanas modales (1200), que
        tienen que poder taparla.

        Abierta se esconde en el celular: ahí el chat ocupa la pantalla entera y la burbuja
        quedaría atrapada detrás del panel, sin nada que hacer. En pantalla ancha el panel es una
        ventanita apoyada justo encima, así que la burbuja sigue a la vista y es la que cierra.
      */
      className={`group focus-visible:outline-primary fixed right-4 bottom-[var(--agent-bubble-bottom)] z-[1150] flex items-center justify-center transition-transform duration-200 hover:scale-105 focus-visible:outline-2 focus-visible:outline-offset-4 active:scale-95 md:right-6 md:bottom-6 ${
        open ? 'max-md:hidden' : ''
      }`}
      style={
        {
          width: SIZE,
          height: SIZE,
          '--agent-bubble-bottom': `${MOBILE_BOTTOM_PX}px`,
        } as React.CSSProperties
      }
    >
      {/*
        Las dos capas de atrás son decoración pura y no anuncian nada. Van con `inset` negativo el
        halo —tiene que asomar por afuera— y a ras el cuerpo.
      */}
      <span aria-hidden="true" className="agent-blob-halo absolute -inset-2 rounded-full" />
      <span aria-hidden="true" className="agent-blob absolute inset-0" />

      {/*
        El ícono NO gira con la gota: es hijo del botón, no de la capa que rota. Va `relative` para
        quedar por encima de las dos capas, que están posicionadas.

        **Los dos dibujos están siempre puestos, uno encima del otro**, y lo que cambia es cuál se
        ve. Hecho con un `open ? <A/> : <B/>` el que sale se desmonta al instante y no hay nada que
        animar: abría con un giro y cerraba de golpe. Apilados en la misma celda de la grilla, el
        que se va gira y se encoge mientras el que llega hace el camino inverso, así que el gesto
        existe en los dos sentidos.

        Giran para lados contrarios a propósito: es el mismo movimiento de ida y de vuelta, no dos
        animaciones distintas.
      */}
      <span
        aria-hidden="true"
        className="relative grid place-items-center transition-transform duration-200 group-hover:scale-110 motion-reduce:transition-none"
      >
        <SparkIcon
          className={`col-start-1 row-start-1 h-7 w-7 text-white transition-all duration-300 ease-out motion-reduce:transition-none ${
            open ? 'rotate-90 scale-50 opacity-0' : 'rotate-0 scale-100 opacity-100'
          }`}
        />
        <CrossIcon
          className={`col-start-1 row-start-1 h-6 w-6 text-white transition-all duration-300 ease-out motion-reduce:transition-none ${
            open ? 'rotate-0 scale-100 opacity-100' : '-rotate-90 scale-50 opacity-0'
          }`}
        />
      </span>
    </button>
  )
}
