import { api } from '@/lib/api'

import type {
  Connector,
  ConnectorDraft,
  SearchCriteria,
  Station,
  StationDetail,
  StationInput,
  StationResult,
} from '../types'

/**
 * Acceso a datos de estaciones.
 *
 * La pantalla nunca llama a `api` ni a `fetch` directamente: llama a estas funciones. El
 * motivo es que el contrato REST y lo que la pantalla necesita no se parecen, y la costura
 * conviene que esté en un solo archivo:
 *
 * - Una estación con sus conectores no existe como recurso. `GET /api/stations` devuelve
 *   `StationResponse`, que no los incluye, y no hay endpoint que dé los conectores de una
 *   estación. El único lugar donde el backend los devuelve agrupados es `GET /api/search`,
 *   así que el listado sale de combinar las dos llamadas. Ver `listStations`.
 * - Guardar una estación no es una llamada sino varias: la estación por un lado y cada
 *   conector por el suyo, con el tipo y la potencia en un endpoint y el estado operativo en
 *   otro. Ver `saveConnectors`.
 *
 * Cuando el backend devuelva los conectores dentro de la estación, este archivo se simplifica
 * solo y ningún componente se entera.
 */

/**
 * Centro y radio con los que `GET /api/search` deja de filtrar nada.
 *
 * La búsqueda descarta por distancia al punto consultado, y no hay forma de pedirle "todas".
 * 20.100 km es algo más de media circunferencia terrestre: desde cualquier punto alcanza al
 * resto del planeta, así que ninguna estación queda afuera.
 */
const EVERYWHERE = { lat: 0, lon: 0, radiusKm: 20100 }

/** Lo que el backend recibe para dar de alta o editar una estación (StationRequest). */
interface StationRequestBody {
  name: string
  address: string
  latitude: number
  longitude: number
  photoUrls: string[]
  nodeId: string | null
}

function toRequestBody(input: StationInput): StationRequestBody {
  return {
    name: input.name,
    address: input.address,
    latitude: input.latitude,
    longitude: input.longitude,
    photoUrls: input.photoUrls,
    nodeId: input.nodeId,
  }
}

/**
 * RF07: búsqueda geolocalizada con filtros.
 *
 * Los nombres de los parámetros de la query NO son los del contrato de dominio: el
 * controlador espera `lat` y `lon` donde `SearchCriteria` dice `latitude` y `longitude`. La
 * traducción vive acá, en el único archivo del frontend que conoce la forma de los endpoints.
 *
 * `connectorType` y `minimumPowerKw` en `undefined` no se envían —el cliente HTTP descarta
 * las claves indefinidas—, y el backend los toma como "sin filtro".
 *
 * @param signal para cancelar la petición si el usuario vuelve a mover el mapa antes de que
 *               responda; sin esto, dos respuestas fuera de orden dejan la lista con los
 *               resultados de la búsqueda vieja.
 */
export function searchStations(
  criteria: SearchCriteria,
  signal?: AbortSignal,
): Promise<StationResult[]> {
  return api.get<StationResult[]>('/search', {
    params: {
      lat: criteria.latitude,
      lon: criteria.longitude,
      radiusKm: criteria.radiusKm,
      connectorType: criteria.connectorType,
      minimumPowerKw: criteria.minimumPowerKw,
      onlyAvailable: criteria.onlyAvailable,
    },
    signal,
  })
}

/**
 * Todas las estaciones como resultados de búsqueda, sin filtrar por distancia ni conector.
 *
 * No se exporta: `distanceKm` sale calculada contra un centro que no es el de nadie, así que
 * solo sirve para lo de acá abajo —sacar los conectores de cada estación—, nunca para
 * mostrarle una distancia a un usuario. Quien necesite eso llama a `searchStations` con el
 * punto que le importa.
 */
function searchEverywhere(signal?: AbortSignal): Promise<StationResult[]> {
  return searchStations(
    {
      latitude: EVERYWHERE.lat,
      longitude: EVERYWHERE.lon,
      radiusKm: EVERYWHERE.radiusKm,
      onlyAvailable: false,
    },
    signal,
  )
}

/**
 * Conectores de todas las estaciones, indexados por estación.
 *
 * Sale de la búsqueda sin filtros, que es el único endpoint que los devuelve agrupados.
 */
async function connectorsByStation(signal?: AbortSignal): Promise<Map<number, Connector[]>> {
  const results = await searchEverywhere(signal)

  return new Map(
    results.map((result) => [
      result.stationId,
      result.matchingConnectors.map((summary) => ({
        id: summary.connectorId,
        connectorType: summary.connectorType,
        maxPowerKw: summary.maxPowerKw,
        operationalStatus: summary.operationalStatus,
      })),
    ]),
  )
}

/**
 * Estaciones dadas de alta, con sus conectores.
 *
 * Las dos llamadas salen juntas y no una detrás de la otra: no dependen entre sí, y en serie
 * la pantalla tardaría el doble en aparecer.
 */
export async function listStations(signal?: AbortSignal): Promise<StationDetail[]> {
  const [stations, connectors] = await Promise.all([
    api.get<Station[]>('/stations', { signal }),
    connectorsByStation(signal),
  ])

  return stations.map((station) => ({
    ...station,
    connectors: connectors.get(station.id) ?? [],
  }))
}

/** Una estación con sus conectores. */
export async function getStation(id: number, signal?: AbortSignal): Promise<StationDetail> {
  const [station, connectors] = await Promise.all([
    api.get<Station>(`/stations/${id}`, { signal }),
    connectorsByStation(signal),
  ])

  return { ...station, connectors: connectors.get(id) ?? [] }
}

/**
 * Lleva los conectores de una estación al estado que dejó el formulario.
 *
 * Hay tres operaciones distintas y ningún endpoint que las haga juntas:
 *
 * - los que el formulario borró se eliminan;
 * - los que ya existían se reconfiguran (tipo y potencia) y, si hace falta, se les cambia el
 *   estado operativo, que va por otro endpoint;
 * - los nuevos se crean —nacen AVAILABLE— y recién después se les pone el estado elegido.
 *
 * Los conectores se guardan de a uno pero en paralelo: son independientes entre sí.
 */
async function saveConnectors(
  stationId: number,
  drafts: ConnectorDraft[],
  previous: Connector[],
): Promise<void> {
  const keptIds = new Set(drafts.map((draft) => draft.id).filter((id) => id !== undefined))

  const removals = previous
    .filter((connector) => !keptIds.has(connector.id))
    .map((connector) => api.delete(`/connectors/${connector.id}`))

  const writes = drafts.map(async (draft) => {
    const body = { connectorType: draft.connectorType, maxPowerKw: draft.maxPowerKw }

    if (draft.id === undefined) {
      const created = await api.post<Connector>(`/stations/${stationId}/connectors`, body)
      if (draft.operationalStatus !== 'AVAILABLE') {
        await api.patch(`/connectors/${created.id}/status`, {
          operationalStatus: draft.operationalStatus,
        })
      }
      return
    }

    await api.post(`/connectors/${draft.id}/configure`, body)

    /* El estado solo se toca si cambió: es una llamada más y la mayoría de las ediciones no lo mueven. */
    const before = previous.find((connector) => connector.id === draft.id)
    if (before?.operationalStatus !== draft.operationalStatus) {
      await api.patch(`/connectors/${draft.id}/status`, {
        operationalStatus: draft.operationalStatus,
      })
    }
  })

  await Promise.all([...removals, ...writes])
}

/** Alta de una estación con sus conectores. */
export async function createStation(input: StationInput): Promise<StationDetail> {
  const station = await api.post<Station>('/stations', toRequestBody(input))
  await saveConnectors(station.id, input.connectors, [])
  /*
   * Se relee en vez de armar el resultado a mano: los conectores recién creados tienen ids
   * que solo conoce el backend, y son los que la pantalla necesita para la próxima edición.
   */
  return getStation(station.id)
}

/** Edición de una estación existente. */
export async function updateStation(id: number, input: StationInput): Promise<StationDetail> {
  const current = await getStation(id)

  await api.put<Station>(`/stations/${id}`, toRequestBody(input))
  await saveConnectors(id, input.connectors, current.connectors)

  return getStation(id)
}

/**
 * Baja lógica, no borrado: la estación puede tener reservas y sesiones colgando.
 *
 * El backend la marca inactiva, y `GET /api/stations` deja de devolverla.
 */
export function deactivateStation(id: number): Promise<void> {
  return api.delete(`/stations/${id}`)
}
