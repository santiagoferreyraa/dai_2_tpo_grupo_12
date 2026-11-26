import { useMemo, useState } from 'react'

import { useSession } from '@/features/auth/session'

import { normalize } from './format'
import { presetPeriod, type Period } from './period'
import { seriesOf, totalsOf, type Series, type Totals } from './series'
import type { DashboardConnector, DashboardStation } from './types'
import { useAnalyticsData, type LoadState } from './useAnalyticsData'

/** Saca o pone ids en el conjunto de los NO elegidos. */
function withToggled(set: Set<number>, ids: number[], exclude: boolean): Set<number> {
  const next = new Set(set)
  for (const id of ids) {
    if (exclude) next.add(id)
    else next.delete(id)
  }
  return next
}

export interface DashboardModel {
  period: Period
  setPeriod: (period: Period) => void
  state: LoadState
  rangeError: string | null
  reload: () => void
  /** "Toda la red" para el administrador, "Mis estaciones" para el operador. */
  scope: string

  query: string
  setQuery: (query: string) => void

  stations: DashboardStation[]
  /** Las estaciones que pasan el buscador: las que muestra la lista. */
  visibleStations: DashboardStation[]
  selectedStationIds: Set<number>
  toggleStation: (station: DashboardStation) => void
  toggleAllStations: (select: boolean) => void
  /** Deja elegida solo esa estación: es lo que hace tocar un pin. */
  focusStation: (stationId: number) => void

  /** Los conectores de las estaciones elegidas: los que muestra la lista. */
  visibleConnectors: DashboardConnector[]
  isConnectorSelected: (connector: DashboardConnector) => boolean
  toggleConnector: (connector: DashboardConnector) => void
  toggleAllConnectors: (select: boolean) => void

  totals: Totals
  series: Series
  /** Cambia cuando cambia lo que se mira: vuelve a disparar la entrada del gráfico. */
  animationKey: string
  hasActivity: boolean
}

/**
 * Todo lo que el tablero sabe y puede hacer, sin decir cómo se dibuja.
 *
 * Existe para que el escritorio y el celular sean **dos disposiciones del mismo tablero** y no dos
 * tableros: el mismo período, la misma selección y las mismas cuentas. Si mañana cambia qué cuenta
 * como "elegido", se cambia acá una vez.
 *
 * **La selección se guarda al revés: lo que NO está elegido.** Por omisión está todo elegido, y
 * así sigue estándolo lo que aparezca después —una estación nueva, los conectores de una estación
 * que se vuelve a tildar— sin que nadie tenga que agregarlo a mano.
 *
 * Los totales, el gráfico y el pico salen de los conectores elegidos de las estaciones elegidas
 * (ver `series.ts`): cambiar la selección no vuelve a pedir nada al backend.
 */
export function useDashboardModel(): DashboardModel {
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

  return {
    period,
    setPeriod,
    state,
    rangeError,
    reload,
    scope: session?.role === 'ADMIN' ? 'Toda la red' : 'Mis estaciones',

    query,
    setQuery,

    stations,
    visibleStations,
    selectedStationIds,
    toggleStation: (station) =>
      setExcludedStations((set) =>
        withToggled(set, [station.id], selectedStationIds.has(station.id)),
      ),
    toggleAllStations: (select) =>
      setExcludedStations((set) =>
        withToggled(
          set,
          visibleStations.map((s) => s.id),
          !select,
        ),
      ),
    focusStation: (id) => {
      setExcludedStations(new Set(stations.filter((s) => s.id !== id).map((s) => s.id)))
      setQuery('')
    },

    visibleConnectors,
    isConnectorSelected: (connector) => !excludedConnectors.has(connector.id),
    toggleConnector: (connector) =>
      setExcludedConnectors((set) => withToggled(set, [connector.id], !set.has(connector.id))),
    toggleAllConnectors: (select) =>
      setExcludedConnectors((set) =>
        withToggled(
          set,
          visibleConnectors.map((c) => c.id),
          !select,
        ),
      ),

    totals,
    series,
    animationKey: `${period.from.getTime()}:${activeConnectors.map((c) => c.id).join(',')}:${state}`,
    hasActivity: activeConnectors.some((c) => c.slots.length > 0),
  }
}
