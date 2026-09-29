/**
 * Las herramientas del agente: lo único que el modelo puede HACER. Todas son de lectura.
 *
 * El modelo no ve la API de Ecopedia: ve estas cuatro funciones con su descripción, decide cuál
 * llamar y con qué, y lee lo que devuelven. Por eso lo que devuelven está recortado a lo que sirve
 * para contestar —nombres, direcciones, distancias, conectores—: cada campo de más es texto que el
 * modelo tiene que leer y un lugar más donde equivocarse.
 *
 * **Ninguna reserva.** Retener o confirmar cobra la seña y se hace desde la pantalla de reservas,
 * donde el conductor ve el precio y acepta la tolerancia. El agente, a lo sumo, le dice adónde ir.
 *
 * Un error de un backend no se lanza: se devuelve como `{ error }`, así el modelo puede contarle
 * al conductor qué pasó ("iniciá sesión", "ese conector está fuera de servicio") en vez de cortar
 * la conversación.
 */

import { tool } from '@strands-agents/sdk'
import { z } from 'zod'

import { EcopediaError, type EcopediaApi, type StationResult } from './ecopedia.ts'
import type { Place } from './geocoding.ts'

export interface ToolDeps {
  api: EcopediaApi
  findPlaces: (query: string, signal?: AbortSignal) => Promise<Place[]>
}

/** Cuántas estaciones se le pasan al modelo por búsqueda. Recomienda de a dos o tres. */
const MAX_STATIONS = 5

/** El radio de búsqueda, acotado: más allá de 50 km ya no es "cerca". */
const MIN_RADIUS_KM = 1
const MAX_RADIUS_KM = 50

const ZONE = 'America/Argentina/Buenos_Aires'

const CONNECTOR_TYPE = z
  .enum(['CCS2', 'CHADEMO', 'TYPE_2'])
  .describe('Tipo de conector: CCS2, CHADEMO o TYPE_2 (Tipo 2).')

export function createTools(deps: ToolDeps) {
  const findPlace = tool({
    name: 'find_place',
    description:
      'Convierte un lugar escrito por el conductor (dirección, esquina, barrio, ciudad o lugar ' +
      'conocido de Argentina) en coordenadas. Devuelve hasta tres candidatos, del más probable al ' +
      'menos. Usala antes de buscar estaciones cuando el conductor dice dónde está.',
    inputSchema: z.object({
      query: z
        .string()
        .describe('El lugar tal como lo escribió el conductor, con la ciudad si la dijo.'),
    }),
    callback: async ({ query }, context) => {
      try {
        const places = await deps.findPlaces(query, context?.cancelSignal)
        if (places.length === 0) return { found: false, message: 'No se encontró ese lugar.' }
        return {
          found: true,
          places: places.map((place) => ({
            label: place.label,
            latitude: round(place.latitude, 5),
            longitude: round(place.longitude, 5),
            precisionMeters: place.precisionM,
          })),
        }
      } catch {
        return {
          error:
            'El buscador de direcciones no responde. Pedile al conductor que lo intente de nuevo.',
        }
      }
    },
  })

  const searchStations = tool({
    name: 'search_stations',
    description:
      'Busca estaciones de carga de Ecopedia cerca de unas coordenadas, ordenadas de la más ' +
      'cercana a la más lejana. La distancia es en línea recta, no por calles. Cada estación trae ' +
      'sus conectores con tipo, potencia en kW y estado (AVAILABLE = libre ahora, OCCUPIED = en ' +
      'uso, OUT_OF_SERVICE = fuera de servicio).',
    inputSchema: z.object({
      latitude: z.number().describe('Latitud del punto de partida.'),
      longitude: z.number().describe('Longitud del punto de partida.'),
      radiusKm: z
        .number()
        .optional()
        .describe(
          'Radio de búsqueda en km. Por omisión 10; si no hay resultados, probá con 25 y después 50.',
        ),
      connectorType: CONNECTOR_TYPE.optional(),
      minimumPowerKw: z
        .number()
        .optional()
        .describe('Potencia mínima en kW, si el conductor la pidió.'),
      onlyAvailable: z
        .boolean()
        .optional()
        .describe('true para traer solo conectores libres en este momento.'),
    }),
    callback: async (input, context) => {
      try {
        const results = await deps.api.searchStations(
          {
            latitude: input.latitude,
            longitude: input.longitude,
            radiusKm: clamp(input.radiusKm ?? 10, MIN_RADIUS_KM, MAX_RADIUS_KM),
            connectorType: input.connectorType,
            minimumPowerKw: input.minimumPowerKw,
            onlyAvailable: input.onlyAvailable,
          },
          context?.cancelSignal,
        )
        return { stations: summarize(results) }
      } catch (error) {
        return { error: explain(error) }
      }
    },
  })

  const getStation = tool({
    name: 'get_station',
    description: 'Trae una estación por su id, con todos sus conectores y el estado de cada uno.',
    inputSchema: z.object({ stationId: z.number().int().describe('El id de la estación.') }),
    callback: async ({ stationId }, context) => {
      try {
        const station = await deps.api.getStation(stationId, context?.cancelSignal)
        return {
          stationId: station.id,
          name: station.name,
          address: station.address,
          active: station.active,
          connectors: station.connectors.map((connector) => ({
            connectorId: connector.id,
            type: connector.connectorType,
            powerKw: connector.maxPowerKw,
            status: connector.operationalStatus,
          })),
        }
      } catch (error) {
        return { error: explain(error) }
      }
    },
  })

  const getAvailability = tool({
    name: 'get_availability',
    description:
      'Devuelve los tramos libres para reservar de un conector entre dos momentos. Necesita que ' +
      'el conductor haya iniciado sesión. Los momentos van en ISO 8601 con la zona de Buenos ' +
      'Aires, por ejemplo 2026-09-30T10:00:00-03:00. Solo informa: no reserva nada.',
    inputSchema: z.object({
      connectorId: z.number().int().describe('El id del conector.'),
      from: z.string().describe('Desde cuándo, ISO 8601 con zona (-03:00).'),
      to: z.string().describe('Hasta cuándo, ISO 8601 con zona (-03:00).'),
    }),
    callback: async ({ connectorId, from, to }, context) => {
      if (!deps.api.hasSession) {
        return {
          error:
            'El conductor no inició sesión, y los horarios solo se ven con sesión. Pedile que ' +
            'inicie sesión y vuelva a preguntar.',
        }
      }

      const start = new Date(from)
      const end = new Date(to)
      if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) {
        return {
          error: 'Las fechas no son válidas: tienen que ser ISO 8601 y "to" posterior a "from".',
        }
      }

      try {
        const windows = await deps.api.getAvailability(
          connectorId,
          start.toISOString(),
          end.toISOString(),
          context?.cancelSignal,
        )
        return {
          timeZone: ZONE,
          freeWindows: windows.map((window) => ({
            from: formatLocal(window.start),
            to: formatLocal(window.end),
          })),
        }
      } catch (error) {
        return { error: explain(error) }
      }
    },
  })

  return [findPlace, searchStations, getStation, getAvailability]
}

/** Lo que el modelo necesita de cada estación, con la distancia redondeada. */
function summarize(results: StationResult[]) {
  return [...results]
    .sort((a, b) => a.distanceKm - b.distanceKm)
    .slice(0, MAX_STATIONS)
    .map((station) => ({
      stationId: station.stationId,
      name: station.name,
      address: station.address,
      distanceKm: round(station.distanceKm, 1),
      connectors: station.matchingConnectors.map((connector) => ({
        connectorId: connector.connectorId,
        type: connector.connectorType,
        powerKw: connector.maxPowerKw,
        status: connector.operationalStatus,
      })),
    }))
}

/**
 * Un error de backend contado para el modelo, que se lo va a contar al conductor.
 *
 * El 401 y el 403 no se devuelven al front como tales: el cliente HTTP del front cierra la sesión
 * ante cualquiera de los dos, y un token vencido se resuelve mejor con un "volvé a iniciar sesión"
 * dentro de la conversación que con un deslogueo en medio del chat.
 *
 * Van juntos porque en Ecopedia un token vencido llega como 403, no como 401: `SecurityConfig`
 * deja pasar todas las URL y quien rechaza es el `@PreAuthorize`. Las herramientas solo llaman a
 * rutas públicas o que piden sesión a secas, así que un 403 acá es siempre la sesión.
 */
function explain(error: unknown): string {
  if (!(error instanceof EcopediaError)) return 'No se pudo consultar Ecopedia.'
  switch (error.status) {
    case 0:
      return 'El servidor de Ecopedia no responde en este momento.'
    case 401:
    case 403:
      return 'La sesión del conductor venció o no es válida. Pedile que vuelva a iniciar sesión.'
    case 404:
      return 'No existe: revisá el id.'
    default:
      return error.message
  }
}

/** "mié 30/09 10:00", en la hora de Buenos Aires. */
function formatLocal(instant: string): string {
  return new Intl.DateTimeFormat('es-AR', {
    timeZone: ZONE,
    weekday: 'short',
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(instant))
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function round(value: number, decimals: number): number {
  const factor = 10 ** decimals
  return Math.round(value * factor) / factor
}
