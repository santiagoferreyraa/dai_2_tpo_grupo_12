/**
 * El cliente de la API de Ecopedia: lo único del agente que sabe hablar con core y con Reservas.
 *
 * El agente no tiene permisos propios. Cada llamada viaja con el token del conductor que escribió
 * en el chat, el mismo que manda el front, así que el agente ve exactamente lo que ese usuario
 * vería en la pantalla, ni más ni menos. Sin sesión, solo llega a lo público: buscar estaciones.
 */

export type ConnectorType = 'CCS2' | 'CHADEMO' | 'TYPE_2'
export type OperationalStatus = 'AVAILABLE' | 'OCCUPIED' | 'OUT_OF_SERVICE'

/** `GET /api/search`: una estación con los conectores que cumplen los filtros. */
export interface StationResult {
  stationId: number
  name: string
  address: string
  latitude: number
  longitude: number
  distanceKm: number
  matchingConnectors: {
    connectorId: number
    connectorType: ConnectorType
    maxPowerKw: number
    operationalStatus: OperationalStatus
  }[]
}

/** `GET /api/stations/{id}`. Solo los campos que usa el agente. */
export interface Station {
  id: number
  name: string
  address: string
  active: boolean
  connectors: {
    id: number
    connectorType: ConnectorType
    maxPowerKw: number
    operationalStatus: OperationalStatus
  }[]
}

/** `GET /api/bookings/availability`: un tramo libre, en instantes UTC. */
export interface FreeWindow {
  start: string
  end: string
}

export interface SearchCriteria {
  latitude: number
  longitude: number
  radiusKm: number
  connectorType?: ConnectorType
  minimumPowerKw?: number
  onlyAvailable?: boolean
}

/** Un error del backend, con el código para que la herramienta decida qué contarle al modelo. */
export class EcopediaError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'EcopediaError'
    this.status = status
  }
}

export interface EcopediaApi {
  /** Si hay token. Sin él, la disponibilidad ni se intenta. */
  readonly hasSession: boolean
  searchStations(criteria: SearchCriteria, signal?: AbortSignal): Promise<StationResult[]>
  getStation(stationId: number, signal?: AbortSignal): Promise<Station>
  getAvailability(
    connectorId: number,
    from: string,
    to: string,
    signal?: AbortSignal,
  ): Promise<FreeWindow[]>
}

export interface EcopediaApiOptions {
  coreUrl: string
  chargingUrl: string
  /** El encabezado `Authorization` tal como llegó del front, o `undefined` sin sesión. */
  authorization: string | undefined
  /** Se inyecta en los tests. */
  fetch?: typeof fetch
}

/** Cuánto se espera a un backend antes de darlo por caído. El modelo espera detrás. */
const TIMEOUT_MS = 8000

export function createEcopediaApi(options: EcopediaApiOptions): EcopediaApi {
  const doFetch = options.fetch ?? fetch

  async function get<T>(baseUrl: string, path: string, signal?: AbortSignal): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (options.authorization !== undefined) headers.Authorization = options.authorization

    const timeout = AbortSignal.timeout(TIMEOUT_MS)
    let response: Response
    try {
      response = await doFetch(`${baseUrl}${path}`, {
        headers,
        signal: signal === undefined ? timeout : AbortSignal.any([signal, timeout]),
      })
    } catch (cause) {
      if (signal?.aborted) throw cause
      throw new EcopediaError('El servidor de Ecopedia no responde', 0)
    }

    if (!response.ok) throw new EcopediaError(await readMessage(response), response.status)
    return (await response.json()) as T
  }

  return {
    hasSession: options.authorization !== undefined,

    searchStations(criteria, signal) {
      const params = new URLSearchParams({
        lat: String(criteria.latitude),
        lon: String(criteria.longitude),
        radiusKm: String(criteria.radiusKm),
      })
      if (criteria.connectorType !== undefined) params.set('connectorType', criteria.connectorType)
      if (criteria.minimumPowerKw !== undefined) {
        params.set('minimumPowerKw', String(criteria.minimumPowerKw))
      }
      if (criteria.onlyAvailable) params.set('onlyAvailable', 'true')
      return get(options.coreUrl, `/api/search?${params.toString()}`, signal)
    },

    getStation(stationId, signal) {
      return get(options.coreUrl, `/api/stations/${encodeURIComponent(stationId)}`, signal)
    },

    getAvailability(connectorId, from, to, signal) {
      const params = new URLSearchParams({ connectorId: String(connectorId), from, to })
      return get(options.chargingUrl, `/api/bookings/availability?${params.toString()}`, signal)
    },
  }
}

/**
 * El motivo que dio el backend. Spring contesta ProblemDetail (`detail`) o un cuerpo propio
 * (`message`); es la misma lectura que hace `frontend/src/lib/api.ts`.
 */
async function readMessage(response: Response): Promise<string> {
  const text = await response.text().catch(() => '')
  try {
    const body = JSON.parse(text) as { detail?: string; message?: string; title?: string }
    return body.detail ?? body.message ?? body.title ?? `Error ${response.status}`
  } catch {
    return text.trim() !== '' ? text.trim() : `Error ${response.status}`
  }
}
