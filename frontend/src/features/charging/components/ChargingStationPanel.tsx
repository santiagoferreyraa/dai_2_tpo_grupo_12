import { useEffect, useState } from 'react'

import StationMiniMap from '@/features/terminals/components/StationMiniMap'
import { listStations } from '@/features/terminals/data/stationsRepository'
import type { StationDetail } from '@/features/terminals/types'

type StationState =
  | { kind: 'loading' }
  | { kind: 'found'; station: StationDetail; stations: StationDetail[] }
  | { kind: 'missing' }

/**
 * Dónde se está cargando: el nombre de la estación y el mapa chico encuadrado sobre ella.
 *
 * **La estación sale del conector y no de la reserva.** Una carga puede arrancar sin reserva
 * (RF10), pero siempre tiene conector, y la red trae cada estación con los suyos. Es el mismo
 * pedido que hace la portada para su mapa, así que no hace falta nada nuevo del backend.
 *
 * Es el mismo `StationMiniMap` de la portada, quieto y sin gestos: acá es para ubicarse, no para
 * buscar otra. Si la estación no aparece —la dieron de baja, o el pedido falló— queda el número
 * de conector y un recuadro vacío: la carga sigue y no puede depender del mapa.
 */
export default function ChargingStationPanel({
  connectorId,
  className = '',
}: {
  connectorId: number
  className?: string
}) {
  const [state, setState] = useState<StationState>({ kind: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    listStations(controller.signal)
      .then((stations) => {
        const station = stations.find((candidate) =>
          candidate.connectors.some((connector) => connector.id === connectorId),
        )
        setState(station === undefined ? { kind: 'missing' } : { kind: 'found', station, stations })
      })
      .catch(() => {
        if (!controller.signal.aborted) setState({ kind: 'missing' })
      })
    return () => controller.abort()
  }, [connectorId])

  return (
    <section
      className={`glass-panel flex min-h-0 flex-col rounded-3xl p-5 xl:p-6 ${className}`}
      aria-label="Dónde estás cargando"
    >
      <h2 className="text-text truncate text-xl font-extrabold tracking-tight xl:text-2xl">
        {state.kind === 'found' ? state.station.name : `Conector ${String(connectorId)}`}
      </h2>
      <p className="text-text-muted mt-0.5 truncate text-sm">
        {state.kind === 'found'
          ? `${state.station.address} · Conector ${String(connectorId)}`
          : state.kind === 'loading'
            ? 'Buscando la estación…'
            : 'No encontramos la estación en el mapa'}
      </p>

      <div className="bg-surface/40 charge-map-shadow relative mt-4 min-h-0 flex-1 overflow-hidden rounded-2xl">
        {state.kind === 'loading' && <div className="absolute inset-0 animate-pulse" />}
        {state.kind === 'found' && (
          <StationMiniMap
            stations={state.stations}
            focus={state.station}
            /*
              Un punto más lejos que en la portada: acá el recuadro es más grande y lo que suma es ver
              las estaciones de alrededor, no la cuadra.
            */
            zoom={13}
            className="absolute inset-0"
          />
        )}
      </div>
    </section>
  )
}
