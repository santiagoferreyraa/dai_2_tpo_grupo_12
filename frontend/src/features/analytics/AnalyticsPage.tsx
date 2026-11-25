import { useMemo, useState } from 'react'

import { useSession } from '@/features/auth/session'

import AnalyticsMap from './components/AnalyticsMap'
import DemandChart from './components/DemandChart'
import PeriodPicker from './components/PeriodPicker'
import SelectionList, { type Column } from './components/SelectionList'
import StatCard from './components/StatCard'
import TabbedPanel from './components/TabbedPanel'
import { formatAmount, formatCount, formatMoment, normalize } from './format'
import { presetPeriod, type Period } from './period'
import { seriesOf, totalsOf } from './series'
import type { DashboardConnector, DashboardStation } from './types'
import { useAnalyticsData } from './useAnalyticsData'

/** El ancho de la pestaña del buscador, en píxeles: lo usan la silueta y el contenido. */
const SEARCH_TAB_WIDTH = 360

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

const LIST_TEMPLATE = 'minmax(0,1fr) 4.5rem 4.5rem'

/** Saca o pone ids en el conjunto de los NO elegidos. */
function withToggled(set: Set<number>, ids: number[], exclude: boolean): Set<number> {
  const next = new Set(set)
  for (const id of ids) {
    if (exclude) next.add(id)
    else next.delete(id)
  }
  return next
}

/**
 * El tablero de Estadísticas (ECO-301, RF16 y RF19), para el administrador y el operador.
 *
 * **La selección se guarda al revés: lo que NO está elegido.** Por omisión está todo elegido, y
 * así sigue estándolo lo que aparezca después —una estación nueva, los conectores de una estación
 * que se vuelve a tildar— sin que nadie tenga que agregarlo a mano.
 *
 * Los totales, el gráfico y el pico salen de los conectores elegidos de las estaciones elegidas
 * (ver `series.ts`): cambiar la selección no vuelve a pedir nada al backend.
 *
 * Es solo de escritorio por ahora: en el celular se avisa que se abra en una pantalla más grande.
 */
export default function AnalyticsPage() {
  const session = useSession()
  const [period, setPeriod] = useState<Period>(() => presetPeriod('24h'))
  const { state, rangeError, stations, connectors, reload } = useAnalyticsData(period)
  const [excludedStations, setExcludedStations] = useState<Set<number>>(() => new Set())
  const [excludedConnectors, setExcludedConnectors] = useState<Set<number>>(() => new Set())
  const [query, setQuery] = useState('')

  const selectedStationIds = useMemo(
    () => new Set(stations.filter((s) => !excludedStations.has(s.id)).map((s) => s.id)),
    [stations, excludedStations],
  )
  const visibleStations = useMemo(() => {
    const q = normalize(query)
    return q === '' ? stations : stations.filter((s) => normalize(s.name).includes(q))
  }, [stations, query])
  const visibleConnectors = useMemo(
    () => connectors.filter((c) => selectedStationIds.has(c.stationId)),
    [connectors, selectedStationIds],
  )
  const activeConnectors = useMemo(
    () => visibleConnectors.filter((c) => !excludedConnectors.has(c.id)),
    [visibleConnectors, excludedConnectors],
  )

  const totals = useMemo(() => totalsOf(activeConnectors), [activeConnectors])
  const series = useMemo(
    () => seriesOf(activeConnectors, period.from, period.to),
    [activeConnectors, period],
  )
  const animationKey = `${period.from.getTime()}:${activeConnectors.map((c) => c.id).join(',')}:${state}`

  const focusStation = (id: number) => {
    setExcludedStations(new Set(stations.filter((s) => s.id !== id).map((s) => s.id)))
    setQuery('')
  }

  const scope = session?.role === 'ADMIN' ? 'Toda la red' : 'Mis estaciones'
  const hasActivity = activeConnectors.some((c) => c.slots.length > 0)

  return (
    <section className="flex min-h-0 flex-1 flex-col">
      <h1 className="sr-only">Estadísticas</h1>

      <div className="flex flex-1 items-center justify-center px-6 pb-36 md:hidden">
        <div className="glass-panel max-w-sm rounded-3xl p-7 text-center">
          <p className="text-text font-semibold">El tablero se ve en una pantalla más grande</p>
          <p className="text-text-muted mt-2 text-sm">
            Abrí Estadísticas desde una computadora o una tablet en horizontal.
          </p>
        </div>
      </div>

      <div className="hidden min-h-0 flex-1 flex-col gap-5 px-6 pb-6 md:flex">
        <div className="grid min-h-0 flex-[1.3] grid-cols-[minmax(0,1.75fr)_minmax(0,1fr)] gap-5">
          <TabbedPanel
            tabWidth={SEARCH_TAB_WIDTH}
            tab={
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
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="Buscar estación"
                  className="text-text placeholder:text-text-muted min-w-0 flex-1 bg-transparent text-sm outline-none"
                />
              </label>
            }
            beside={<PeriodPicker period={period} onChange={setPeriod} rangeError={rangeError} />}
          >
            <div className="grid min-h-0 flex-1 grid-cols-[minmax(9.5rem,12rem)_minmax(0,1fr)] gap-4">
              <div className="flex min-h-0 flex-col gap-3">
                <StatCard
                  label="Energía entregada"
                  value={totals.energyKwh}
                  unit="kWh"
                  format={formatAmount}
                />
                <StatCard
                  label="Sesiones"
                  value={totals.sessions}
                  format={(v) => formatCount(Math.round(v))}
                  delayMs={60}
                />
                <StatCard
                  label="Potencia simultánea"
                  value={totals.peakKw}
                  unit="kW"
                  format={formatAmount}
                  caption={
                    totals.peakAt === null
                      ? 'Sin cargas en el período'
                      : `Pico ${formatMoment(totals.peakAt)}`
                  }
                  delayMs={120}
                />
              </div>

              <div
                className="glass-inset analytics-rise relative flex min-h-0 flex-col rounded-2xl p-4"
                style={{ ['--rise-delay' as string]: '80ms' }}
              >
                <div className="mb-2 flex shrink-0 items-baseline justify-between gap-3">
                  <h2 className="text-text text-sm font-bold">Demanda por franja</h2>
                  <span className="text-text-muted truncate text-xs">
                    {scope} ·{' '}
                    {series.bucketMinutes < 60
                      ? 'franjas de 15 min'
                      : `barras de ${series.bucketMinutes / 60} h`}
                  </span>
                </div>
                <DemandChart
                  series={series}
                  from={period.from}
                  to={period.to}
                  animationKey={animationKey}
                />

                {state !== 'ready' || !hasActivity ? (
                  <div className="pointer-events-none absolute inset-0 grid place-items-center">
                    {state === 'loading' && (
                      <p className="text-text-muted text-sm animate-pulse">
                        Cargando estadísticas…
                      </p>
                    )}
                    {state === 'error' && (
                      <div className="pointer-events-auto flex flex-col items-center gap-3 text-center">
                        <p className="text-danger text-sm" role="alert">
                          {rangeError ?? 'No pudimos traer las estadísticas.'}
                        </p>
                        <button
                          type="button"
                          onClick={reload}
                          className="brand-fill text-on-primary cursor-pointer rounded-xl px-4 py-2 text-sm font-bold"
                        >
                          Reintentar
                        </button>
                      </div>
                    )}
                    {state === 'ready' && (
                      <p className="text-text-muted text-sm">
                        {selectedStationIds.size === 0
                          ? 'Elegí al menos una estación.'
                          : 'Todavía no hubo cargas en este período.'}
                      </p>
                    )}
                  </div>
                ) : null}
              </div>
            </div>
          </TabbedPanel>

          <div
            className="glass-panel analytics-rise relative min-h-0 overflow-hidden rounded-3xl"
            style={{ marginTop: 56, ['--rise-delay' as string]: '100ms' }}
          >
            <AnalyticsMap
              stations={stations}
              selectedIds={selectedStationIds}
              onFocus={focusStation}
            />
          </div>
        </div>

        <div
          className="glass-panel analytics-rise grid min-h-0 flex-1 grid-cols-[minmax(0,1fr)_1px_minmax(0,1fr)] gap-6 rounded-3xl p-5"
          style={{ ['--rise-delay' as string]: '160ms' }}
        >
          <SelectionList
            title="Estaciones"
            rows={visibleStations}
            rowKey={(s) => s.id}
            isSelected={(s) => selectedStationIds.has(s.id)}
            onToggle={(s) =>
              setExcludedStations((set) => withToggled(set, [s.id], selectedStationIds.has(s.id)))
            }
            onToggleAll={(select) =>
              setExcludedStations((set) =>
                withToggled(
                  set,
                  visibleStations.map((s) => s.id),
                  !select,
                ),
              )
            }
            columns={STATION_COLUMNS}
            template={LIST_TEMPLATE}
            empty={
              stations.length === 0
                ? state === 'loading'
                  ? 'Cargando estaciones…'
                  : 'Todavía no hay estaciones para mostrar.'
                : 'Ninguna estación coincide con la búsqueda.'
            }
          />

          <div className="bg-border/70 w-px self-stretch" aria-hidden="true" />

          <SelectionList
            title="Conectores"
            rows={visibleConnectors}
            rowKey={(c) => c.id}
            isSelected={(c) => !excludedConnectors.has(c.id)}
            onToggle={(c) =>
              setExcludedConnectors((set) => withToggled(set, [c.id], !set.has(c.id)))
            }
            onToggleAll={(select) =>
              setExcludedConnectors((set) =>
                withToggled(
                  set,
                  visibleConnectors.map((c) => c.id),
                  !select,
                ),
              )
            }
            columns={CONNECTOR_COLUMNS}
            template={LIST_TEMPLATE}
            empty={
              selectedStationIds.size === 0
                ? 'Elegí una estación para ver sus conectores.'
                : 'Las estaciones elegidas no tienen conectores.'
            }
          />
        </div>
      </div>
    </section>
  )
}
