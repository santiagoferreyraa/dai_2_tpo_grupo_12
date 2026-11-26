/**
 * El pin de una estación en el mapa del tablero.
 *
 * Es el mismo redondel con el rayo de `terminals/stationPin.ts` —mismo tamaño, mismo badge
 * colgando, mismo halo al elegirlo—, con dos cambios: el color dice si la estación está en la
 * selección (verde) o no (amarillo, ver `analytics.css`), y el badge muestra lo que entregó en el
 * período en vez de la potencia del conector más rápido.
 */

import L from 'leaflet'

import { formatAmount } from './format'

/* Clases completas en cada rama, nunca armadas por concatenación: Tailwind busca los nombres
   como texto plano y no genera los que no encuentra. Ver `stationPin.ts`. */
const SELECTED_RING = 'border-primary text-primary station-pin__ring--selected'
const IDLE_RING = 'analytics-pin--idle'

const BOLT_SVG =
  '<svg viewBox="0 0 24 24" fill="currentColor" class="h-5 w-5" aria-hidden="true">' +
  '<path d="M13 2 4.5 13.5H11l-1 8.5 8.5-11.5H12l1-8.5Z" /></svg>'

/** Solo se interpola un número ya formateado: un nombre de estación nunca entra a este HTML. */
export function analyticsPin(energyKwh: number, selected: boolean): L.DivIcon {
  const ring = selected ? SELECTED_RING : IDLE_RING
  return L.divIcon({
    className: '',
    html: `
      <div class="flex flex-col items-center transition-opacity duration-300">
        <div class="station-pin__ring ${ring} bg-background grid h-10 w-10 place-items-center rounded-full border-2 shadow-md transition-colors duration-300">
          ${BOLT_SVG}
        </div>
        <span class="station-pin__badge border-border bg-background text-text mt-1 rounded-full border px-2 py-0.5 text-xs font-semibold whitespace-nowrap shadow-sm">
          ${formatAmount(energyKwh)} kWh
        </span>
      </div>`,
    iconSize: [40, 64],
    iconAnchor: [20, 20],
  })
}
