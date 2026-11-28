import { useEffect, useMemo, useState } from 'react'

import { useSession } from '@/features/auth/session'
import { listStations } from '@/features/terminals/data/stationsRepository'
import { CONNECTOR_TYPE_LABEL } from '@/features/terminals/format'
import type { ConnectorType, StationDetail } from '@/features/terminals/types'
import { ApiError } from '@/lib/api'

import { fetchAnalytics } from './data/analyticsRepository'
import type { Period } from './period'
import {
  UNKNOWN_STATION_ID,
  type AnalyticsResponse,
  type DashboardConnector,
  type DashboardStation,
} from './types'

export type LoadState = 'loading' | 'ready' | 'error'

export interface AnalyticsData {
  state: LoadState
  /** Mensaje del backend cuando el error es el rango, para decirlo junto a las fechas. */
  rangeError: string | null
  stations: DashboardStation[]
  connectors: DashboardConnector[]
  reload: () => void
}

/**
 * Las estaciones que se ven y lo que cargó cada una en el período.
 *
 * **Las estaciones salen de core, no del tablero.** El tablero solo trae las que tuvieron
 * lecturas, y la lista tiene que mostrar todas las del operador —con cero si no cargaron—, que es
 * además lo que dibuja el mapa. Para el administrador son todas; para el operador, las suyas: el
 * mismo filtro que la pantalla de Estaciones, porque core no tiene un "mis estaciones".
 *
 * Las estaciones se piden una vez; el tablero, cada vez que cambia el período.
 */
export function useAnalyticsData(period: Period): AnalyticsData {
  const session = useSession()
  const [attempt, setAttempt] = useState(0)
  /*
    Cada respuesta se guarda junto con el pedido que la trajo. "Cargando" no es un estado que se
    escribe: es que lo último que llegó no corresponde a lo que se está pidiendo ahora. Así, al
    cambiar de período, el gráfico anterior sigue en pantalla hasta que llega el nuevo.
  */
  const [stationsResult, setStationsResult] = useState<{
    attempt: number
    rows: StationDetail[] | null
  } | null>(null)
  const [dashboardResult, setDashboardResult] = useState<{
    period: Period
    attempt: number
    response: AnalyticsResponse | null
    rangeError: string | null
  } | null>(null)
  /* La última respuesta buena, para no vaciar la pantalla mientras llega la siguiente. */
  const [lastDashboard, setLastDashboard] = useState<AnalyticsResponse | null>(null)

  const userId = session?.userId ?? null
  const isAdmin = session?.role === 'ADMIN'

  useEffect(() => {
    const controller = new AbortController()
    listStations(controller.signal)
      .then((rows) => {
        setStationsResult({
          attempt,
          rows: rows.filter((s) => s.active && (isAdmin || s.ownerId === userId)),
        })
      })
      .catch(() => {
        if (!controller.signal.aborted) setStationsResult({ attempt, rows: null })
      })
    return () => controller.abort()
  }, [isAdmin, userId, attempt])

  useEffect(() => {
    const controller = new AbortController()
    fetchAnalytics(period.from, period.to, controller.signal)
      .then((response) => {
        setDashboardResult({ period, attempt, response, rangeError: null })
        setLastDashboard(response)
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return
        // El 400 es el rango que el backend no acepta; su `detail` no se muestra (directiva 6).
        const rangeError =
          error instanceof ApiError && error.status === 400
            ? 'Revisá el rango de fechas y probá de nuevo.'
            : null
        setDashboardResult({ period, attempt, response: null, rangeError })
      })
    return () => controller.abort()
  }, [period, attempt])

  const stations = stationsResult?.rows ?? null
  const currentDashboard =
    dashboardResult !== null &&
    dashboardResult.period === period &&
    dashboardResult.attempt === attempt
      ? dashboardResult
      : null
  const stationsFailed = stationsResult !== null && stationsResult.rows === null

  let state: LoadState = 'loading'
  if (stationsFailed || (currentDashboard !== null && currentDashboard.response === null))
    state = 'error'
  else if (currentDashboard !== null && stations !== null) state = 'ready'

  const merged = useMemo(() => merge(stations ?? [], lastDashboard), [stations, lastDashboard])

  return {
    state,
    rangeError: currentDashboard?.rangeError ?? null,
    stations: merged.stations,
    connectors: merged.connectors,
    reload: () => setAttempt((n) => n + 1),
  }
}

function typeLabel(type: string | null): string {
  if (type === null) return 'Sin tipo'
  return CONNECTOR_TYPE_LABEL[type as ConnectorType] ?? type
}

/**
 * Cruza las estaciones de core con el tablero.
 *
 * Lo que llegó sin estación —core no respondía cuando Estadísticas recibió la lectura— va a una
 * estación aparte, "Sin identificar", que aparece en las listas pero no en el mapa.
 */
function merge(
  stations: StationDetail[],
  dashboard: AnalyticsResponse | null,
): { stations: DashboardStation[]; connectors: DashboardConnector[] } {
  const demandByStation = new Map(
    (dashboard?.stations ?? []).map((s) => [s.stationId ?? UNKNOWN_STATION_ID, s]),
  )
  const deliveryByConnector = new Map((dashboard?.connectors ?? []).map((c) => [c.connectorId, c]))

  const resultStations: DashboardStation[] = []
  const resultConnectors: DashboardConnector[] = []
  const seenConnectors = new Set<number>()

  for (const station of stations) {
    const demand = demandByStation.get(station.id)
    resultStations.push({
      id: station.id,
      name: station.name,
      position: { latitude: station.latitude, longitude: station.longitude },
      energyKwh: demand?.energyKwh ?? 0,
      sessions: demand?.sessions ?? 0,
      peakKw: demand?.peakSimultaneousPowerKw ?? 0,
    })
    for (const connector of station.connectors) {
      const delivery = deliveryByConnector.get(connector.id)
      seenConnectors.add(connector.id)
      resultConnectors.push({
        id: connector.id,
        stationId: station.id,
        stationName: station.name,
        type: typeLabel(connector.connectorType),
        energyKwh: delivery?.energyKwh ?? 0,
        sessions: delivery?.sessions ?? 0,
        peakPowerKw: delivery?.peakPowerKw ?? 0,
        slots: delivery?.slots ?? [],
      })
    }
  }

  /*
    Conectores con lecturas que core no listó: o llegaron sin estación, o su estación se dio de
    baja después. Los segundos tienen estación pero no está en la lista; los dos van a "Sin
    identificar" para que su energía no desaparezca de los totales.
  */
  const knownStations = new Set(stations.map((s) => s.id))
  const orphans = (dashboard?.connectors ?? []).filter((c) => !seenConnectors.has(c.connectorId))
  const unknownStationOrphans = orphans.filter(
    (c) => c.stationId === null || !knownStations.has(c.stationId),
  )
  for (const c of orphans) {
    const station = stations.find((s) => s.id === c.stationId)
    if (station === undefined) continue
    resultConnectors.push({
      id: c.connectorId,
      stationId: station.id,
      stationName: station.name,
      type: typeLabel(c.connectorType),
      energyKwh: c.energyKwh,
      sessions: c.sessions,
      peakPowerKw: c.peakPowerKw,
      slots: c.slots,
    })
  }

  if (unknownStationOrphans.length > 0) {
    const energy = unknownStationOrphans.reduce((t, c) => t + c.energyKwh, 0)
    const sessions = unknownStationOrphans.reduce((t, c) => t + c.sessions, 0)
    resultStations.push({
      id: UNKNOWN_STATION_ID,
      name: 'Sin identificar',
      position: null,
      energyKwh: energy,
      sessions,
      peakKw: Math.max(...unknownStationOrphans.map((c) => c.peakDemandKw)),
    })
    for (const c of unknownStationOrphans) {
      resultConnectors.push({
        id: c.connectorId,
        stationId: UNKNOWN_STATION_ID,
        stationName: c.stationName ?? 'Sin identificar',
        type: typeLabel(c.connectorType),
        energyKwh: c.energyKwh,
        sessions: c.sessions,
        peakPowerKw: c.peakPowerKw,
        slots: c.slots,
      })
    }
  }

  resultStations.sort((a, b) => b.energyKwh - a.energyKwh || a.name.localeCompare(b.name))
  resultConnectors.sort((a, b) => b.energyKwh - a.energyKwh || a.id - b.id)
  return { stations: resultStations, connectors: resultConnectors }
}
