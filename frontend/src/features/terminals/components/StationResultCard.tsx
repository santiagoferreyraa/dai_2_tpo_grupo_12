/**
 * Ficha de un resultado de búsqueda, tal como la muestra el carrusel del mapa.
 *
 * Es la tarjeta del conductor y no la del operador: trabaja sobre `StationResult` —lo que
 * devuelve la búsqueda, con los conectores que pasaron el filtro— mientras que la del ABM
 * (components/StationCard.tsx) trabaja sobre `StationDetail`. Son dos pantallas distintas
 * con dos audiencias distintas; lo que comparten, y lo que importa que no se bifurque, son
 * los datos derivados de format.ts.
 *
 * No fija su ancho: lo pone quien la usa.
 */

import {
  availableConnectorCount,
  CONNECTOR_TYPE_LABEL,
  fastestConnector,
  isAvailable,
  maxPowerKw,
  STATUS_LABEL,
} from '../format'
import type { StationResult } from '../types'

/* Clases completas en cada rama, nunca concatenadas: ver el comentario de stationPin.ts. */
const DOT_AVAILABLE = 'brand-fill'
const DOT_UNAVAILABLE = 'bg-text-muted'

const STATUS_TEXT = {
  AVAILABLE: 'text-primary',
  OCCUPIED: 'text-text',
  OUT_OF_SERVICE: 'text-text-muted',
}

interface StationResultCardProps {
  station: StationResult
  /** Con el detalle de cada conector. Solo la ficha elegida del carrusel lo despliega. */
  expanded?: boolean
}

export default function StationResultCard({ station, expanded = false }: StationResultCardProps) {
  const fastest = fastestConnector(station)
  const free = availableConnectorCount(station)

  return (
    <article className="border-border bg-background/95 w-full border p-3 shadow-lg">
      <div className="flex items-center gap-2">
        <h2 className="text-text min-w-0 flex-1 truncate text-sm font-semibold">{station.name}</h2>

        {/*
          El punto repite lo que ya dice el color del pin, y está bien que lo repita: la ficha
          puede quedar sobre otra estación y el pin de esta queda tapado por la propia ficha.
        */}
        <span
          className={`h-2 w-2 shrink-0 rounded-full ${
            isAvailable(station) ? DOT_AVAILABLE : DOT_UNAVAILABLE
          }`}
          role="img"
          aria-label={isAvailable(station) ? 'Disponible' : 'Sin conectores libres'}
        />

        {fastest !== null && (
          <span className="border-border text-text-muted shrink-0 rounded-md border px-1.5 py-0.5 text-[10px]">
            {CONNECTOR_TYPE_LABEL[fastest.connectorType]}
          </span>
        )}
      </div>

      <p className="text-text-muted mt-1 text-xs">{station.address}</p>

      <div className="border-border mt-3 flex items-end gap-5 border-t pt-3">
        <div>
          <p className="text-text-muted text-[10px]">Potencia máx</p>
          <p className="text-text text-sm font-semibold">{maxPowerKw(station)} kW</p>
        </div>

        <div>
          <p className="text-text-muted text-[10px]">Libres</p>
          <p className="text-text text-sm font-semibold">
            {free}/{station.matchingConnectors.length}
          </p>
        </div>

        <div>
          <p className="text-text-muted text-[10px]">Distancia</p>
          <p className="text-text text-sm font-semibold">{station.distanceKm.toFixed(1)} km</p>
        </div>
      </div>

      {expanded && (
        <ul className="border-border mt-3 flex flex-col gap-2 border-t pt-3">
          {station.matchingConnectors.map((connector) => (
            <li key={connector.connectorId} className="flex items-baseline gap-2 text-xs">
              <span className="text-text flex-1">
                {CONNECTOR_TYPE_LABEL[connector.connectorType]}
              </span>
              <span className="text-text-muted">{connector.maxPowerKw} kW</span>
              <span className={`w-28 text-right ${STATUS_TEXT[connector.operationalStatus]}`}>
                {STATUS_LABEL[connector.operationalStatus]}
              </span>
            </li>
          ))}
        </ul>
      )}
    </article>
  )
}
