/**
 * Tipos de la feature Estadísticas (ECO-301).
 *
 * Los de arriba espejan `GET /api/analytics` de `ecopedia-async` (ver `AnalyticsResponse` en el
 * backend). Los de abajo son los de la pantalla, que cruza ese tablero con las estaciones de core
 * para tener nombre, ubicación y conectores aunque no hayan cargado nada en el período.
 */

export type AnalyticsScope = 'NETWORK' | 'OPERATOR'

/** Una franja de 15 minutos con lecturas. `demandKw` es la potencia media: kWh por cuarto de hora. */
export interface SlotDemand {
  start: string
  energyKwh: number
  demandKw: number
  sessionsStarted: number
}

export interface StationDemand {
  /** `null` si core no respondía cuando llegaron las lecturas. */
  stationId: number | null
  stationName: string | null
  energyKwh: number
  sessions: number
  peakSimultaneousPowerKw: number
  peakSimultaneousAt: string | null
  slots: SlotDemand[]
}

export interface ConnectorDelivery {
  connectorId: number
  stationId: number | null
  stationName: string | null
  connectorType: string | null
  energyKwh: number
  sessions: number
  peakPowerKw: number
  peakDemandKw: number
  slots: SlotDemand[]
}

export interface AnalyticsResponse {
  scope: AnalyticsScope
  from: string
  to: string
  slotMinutes: number
  totals: {
    energyKwh: number
    sessions: number
    peakSimultaneousPowerKw: number
    peakSimultaneousAt: string | null
  }
  stations: StationDemand[]
  connectors: ConnectorDelivery[]
}

/*
 * ---------------------------------------------------------------------------
 * Tipos de la pantalla
 * ---------------------------------------------------------------------------
 */

/** Las lecturas que llegaron sin estación se juntan acá: no tienen dónde ir en el mapa. */
export const UNKNOWN_STATION_ID = -1

export interface DashboardStation {
  id: number
  name: string
  /** `null` para la estación sin identificar, que no se dibuja en el mapa. */
  position: { latitude: number; longitude: number } | null
  energyKwh: number
  sessions: number
  peakKw: number
}

export interface DashboardConnector {
  id: number
  stationId: number
  stationName: string
  type: string
  energyKwh: number
  sessions: number
  peakPowerKw: number
  slots: SlotDemand[]
}
