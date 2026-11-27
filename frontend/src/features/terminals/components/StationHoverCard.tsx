/**
 * Tarjeta que aparece al dejar el mouse sobre un pin del mapa.
 *
 * Es un componente aparte de StationResultCard —la ficha del carrusel— aunque muestren datos
 * parecidos. Antes eran uno solo, y tenía sentido mientras el diseño era el mismo; dejó de
 * tenerlo cuando esta pasó a llevar el círculo montado sobre su borde izquierdo y otra
 * jerarquía tipográfica. Lo que sí comparten, que es lo que importa que no se bifurque, son
 * los datos derivados de format.ts.
 *
 * El círculo de la izquierda NO es el pin del mapa: es propio, y queda justo encima del pin,
 * tapándolo. Se dibuja acá y no allá por el orden de capas de Leaflet — el pane de los
 * tooltips va por encima del de los marcadores, así que un pin nunca podría pisar la tarjeta.
 * Al ser parte de la tarjeta, el círculo y el borde forman una sola pieza continua. Es la
 * única forma redonda que queda: la tarjeta va en ángulo recto, igual que el buscador, los
 * filtros y el panel de detalle.
 *
 * Muestra poco a propósito: nombre, dirección, potencia y libres. Es lo que se lee de paso,
 * mientras el mouse va camino a otro lado; el tipo de conector y el resto del detalle están
 * en el panel, a un click del pin.
 *
 * Y no recibe clicks: los tooltips de Leaflet no son interactivos salvo que se los declare
 * así, y aun entonces el mouse tendría que cruzar el hueco entre el pin y la tarjeta sin que
 * se cierre. Lo que abre el detalle es el pin, no esto.
 */

import { availableConnectorCount, isAvailable, maxPowerKw } from '../format'
import type { StationResult } from '../types'

/* Clases completas en cada rama, nunca concatenadas: ver el comentario de stationPin.ts. */
const DOT_AVAILABLE = 'brand-fill'
const DOT_UNAVAILABLE = 'bg-text-muted'

/*
  El borde de la tarjeta y su círculo siguen la disponibilidad, igual que el pin, y no van
  siempre en verde. Si no, pasar el mouse por una estación fuera de servicio la pintaría de
  verde justo cuando el mapa la está mostrando en gris, que es la señal contraria.
*/
const ACCENT_AVAILABLE = 'border-primary'
const ACCENT_UNAVAILABLE = 'border-text-muted'
const CIRCLE_AVAILABLE = 'border-primary text-primary'
const CIRCLE_UNAVAILABLE = 'border-text-muted text-text-muted'

interface StationHoverCardProps {
  station: StationResult
}

export default function StationHoverCard({ station }: StationHoverCardProps) {
  const free = availableConnectorCount(station)
  const available = isAvailable(station)

  return (
    <article
      className={`bg-background relative w-80 border-2 py-3 pr-4 pl-9 shadow-2xl ${
        available ? ACCENT_AVAILABLE : ACCENT_UNAVAILABLE
      }`}
    >
      {/*
        Un poco más grande que el pin (44 contra 40) para taparlo entero: alineado al píxel
        exacto sería frágil, y con dos círculos casi iguales se vería asomar el borde de abajo.
      */}
      <span
        aria-hidden="true"
        className={`bg-background absolute top-1/2 left-0 grid h-11 w-11 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full border-2 ${
          available ? CIRCLE_AVAILABLE : CIRCLE_UNAVAILABLE
        }`}
      >
        <svg viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5">
          <path d="M13 2 4.5 13.5H11l-1 8.5 8.5-11.5H12l1-8.5Z" />
        </svg>
      </span>

      <div className="flex items-center gap-2">
        <h2 className="text-text min-w-0 truncate text-base font-semibold">{station.name}</h2>

        <span
          className={`h-2.5 w-2.5 shrink-0 rounded-full ${
            available ? DOT_AVAILABLE : DOT_UNAVAILABLE
          }`}
          role="img"
          aria-label={available ? 'Disponible' : 'Sin conectores libres'}
        />
      </div>

      <p className="text-text-muted mt-2 flex items-center gap-2 text-sm">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="h-4 w-4 shrink-0"
          aria-hidden="true"
        >
          <path d="M12 21s7-5.5 7-11a7 7 0 1 0-14 0c0 5.5 7 11 7 11Z" />
          <circle cx="12" cy="10" r="2.5" />
        </svg>
        <span className="min-w-0 truncate">{station.address}</span>
      </p>

      <div className="mt-3 flex items-end gap-6">
        <div>
          <p className="text-text-muted text-[11px]">Potencia máx</p>
          <p className="text-text text-base font-semibold">{maxPowerKw(station)} kW</p>
        </div>

        <div>
          <p className="text-text-muted text-[11px]">Libres</p>
          <p className="text-text text-base font-semibold">
            {free}/{station.matchingConnectors.length}
          </p>
        </div>
      </div>
    </article>
  )
}
