/**
 * Las piezas que el escritorio y el celular comparten tal cual: el buscador, el cartel que va
 * sobre el gráfico y las dos listas. Cambia dónde van, no qué son.
 */

import { formatAmount } from '../format'
import type { DashboardConnector, DashboardStation } from '../types'
import type { DashboardModel } from '../useDashboardModel'
import SelectionList, { type Column } from './SelectionList'

export function SearchField({ model }: { model: DashboardModel }) {
  return (
    <label className="glass-inset focus-within:ring-primary flex w-full items-center gap-2 rounded-full px-3.5 py-2 focus-within:ring-1">
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        className="text-text-muted h-4 w-4 shrink-0"
        aria-hidden="true"
      >
        <circle cx="11" cy="11" r="7" />
        <path d="m20 20-3.5-3.5" />
      </svg>
      <span className="sr-only">Buscar estación</span>
      <input
        type="search"
        value={model.query}
        onChange={(event) => model.setQuery(event.target.value)}
        placeholder="Buscar estación"
        className="text-text placeholder:text-text-muted min-w-0 flex-1 bg-transparent text-sm outline-none"
      />
    </label>
  )
}

/** Lo que tapa el gráfico cuando no hay barras que mostrar: cargando, error o vacío. */
export function ChartStatus({ model }: { model: DashboardModel }) {
  const { state, hasActivity } = model
  if (state === 'ready' && hasActivity) return null

  return (
    <div className="pointer-events-none absolute inset-0 grid place-items-center px-6 text-center">
      {state === 'loading' && (
        <p className="text-text-muted animate-pulse text-sm">Cargando estadísticas…</p>
      )}
      {state === 'error' && (
        <div className="pointer-events-auto flex flex-col items-center gap-3">
          <p className="text-danger text-sm" role="alert">
            {model.rangeError ?? 'No pudimos traer las estadísticas.'}
          </p>
          <button
            type="button"
            onClick={model.reload}
            className="brand-fill text-on-primary cursor-pointer rounded-xl px-4 py-2 text-sm font-bold"
          >
            Reintentar
          </button>
        </div>
      )}
      {state === 'ready' && (
        <p className="text-text-muted text-sm">
          {model.selectedStationIds.size === 0
            ? 'Elegí al menos una estación.'
            : 'Todavía no hubo cargas en este período.'}
        </p>
      )}
    </div>
  )
}

const STATION_COLUMNS: Column<DashboardStation>[] = [
  { header: 'Estación', render: (s) => s.name },
  { header: 'kWh', align: 'right', render: (s) => formatAmount(s.energyKwh) },
  { header: 'Pico kW', align: 'right', render: (s) => formatAmount(s.peakKw) },
]

const CONNECTOR_COLUMNS: Column<DashboardConnector>[] = [
  {
    header: 'Conector',
    render: (c) => (
      <>
        <span className="text-text font-semibold">#{c.id}</span>
        <span className="ml-2">{c.type}</span>
        <span className="text-text-muted ml-2 text-xs">{c.stationName}</span>
      </>
    ),
  },
  { header: 'kWh', align: 'right', render: (c) => formatAmount(c.energyKwh) },
  { header: 'Máx kW', align: 'right', render: (c) => formatAmount(c.peakPowerKw) },
]

export function StationList({ model, template }: { model: DashboardModel; template: string }) {
  return (
    <SelectionList
      title="Estaciones"
      rows={model.visibleStations}
      rowKey={(s) => s.id}
      isSelected={(s) => model.selectedStationIds.has(s.id)}
      onToggle={model.toggleStation}
      onToggleAll={model.toggleAllStations}
      columns={STATION_COLUMNS}
      template={template}
      empty={
        model.stations.length === 0
          ? model.state === 'loading'
            ? 'Cargando estaciones…'
            : 'Todavía no hay estaciones para mostrar.'
          : 'Ninguna estación coincide con la búsqueda.'
      }
    />
  )
}

export function ConnectorList({ model, template }: { model: DashboardModel; template: string }) {
  return (
    <SelectionList
      title="Conectores"
      rows={model.visibleConnectors}
      rowKey={(c) => c.id}
      isSelected={model.isConnectorSelected}
      onToggle={model.toggleConnector}
      onToggleAll={model.toggleAllConnectors}
      columns={CONNECTOR_COLUMNS}
      template={template}
      empty={
        model.selectedStationIds.size === 0
          ? 'Elegí una estación para ver sus conectores.'
          : 'Las estaciones elegidas no tienen conectores.'
      }
    />
  )
}
