import { fetchAllStations } from '@/features/terminals/data/allStations'
import type { StationResult } from '@/features/terminals/types'
import { api } from '@/lib/api'

import type {
  Booking,
  BookingLocation,
  BookingResponse,
  FreeWindow,
  FreeWindowResponse,
  Hold,
  HoldRequest,
  HoldResponse,
} from '../types'

/**
 * Acceso a datos de reservas: las llamadas a `/api/bookings` del frontend.
 *
 * Existe por lo mismo que `stationsRepository` y `authRepository`: ninguna pantalla escribe una
 * ruta ni llama a `fetch`. Las rutas van sin `/api` porque lo agrega el cliente, y el proxy de
 * Vite las desvía a `ecopedia-charging` (8082), que es otro proceso distinto de core.
 *
 * **Lo que agrega este archivo sobre el contrato son dos traducciones**, y por eso está acá y no
 * repartido en los componentes:
 *
 * - Las fechas llegan como texto ISO-8601 y salen como `Date`. La franja cuenta cuánto falta y la
 *   lista ordena por horario, y las dos necesitan fechas de verdad.
 * - La reserva trae solo el id del conector, y la pantalla necesita saber dónde es. La estación se
 *   busca en la red que ya carga el buscador (`fetchAllStations`), que la pide una sola vez y la
 *   recuerda: resolver la ubicación no agrega ninguna llamada al backend.
 *
 * **Todas piden rol CONDUCTOR.** Con otro rol el backend contesta 403. No cierra la sesión —el
 * cliente solo la cierra si el token venció—, pero no tiene sentido pedirlas: quien las use tiene
 * que mirar el rol antes.
 *
 * Los errores salen como `ApiError` con el mensaje del backend, que ya viene en castellano y
 * nombra el problema. Los códigos que conviene distinguir en pantalla están en cada función.
 */

/**
 * RF08: retiene un slot mientras el conductor confirma.
 *
 * Los extremos se mandan como ISO-8601 en UTC. El backend los trunca a microsegundos, y un `Date`
 * nunca pasa de milisegundos, así que lo que se manda es exactamente lo que queda guardado.
 *
 * Errores que la pantalla tiene que tratar aparte:
 * - **409**: la ventana ya está retenida o reservada, o el conector está fuera de servicio.
 * - **400**: la ventana ya empezó, dura más de lo permitido o está demasiado lejos.
 * - **404**: el conector no existe.
 * - **503**: no se pudo verificar el conector contra core; se puede reintentar.
 */
export async function startHold(connectorId: number, start: Date, end: Date): Promise<Hold> {
  const request: HoldRequest = {
    connectorId,
    start: start.toISOString(),
    end: end.toISOString(),
  }
  const response = await api.post<HoldResponse>('/bookings/holds', request)
  return toHold(response)
}

/**
 * ECO-33: los huecos libres de un conector entre dos instantes, en orden.
 *
 * Libre es lo que nadie tiene comprometido: ni una reserva confirmada ni una retención vigente,
 * **incluida una retención del propio conductor**. El backend recorta el rango a lo reservable
 * —desde ahora y hasta el horizonte—, así que pedir de más no es un error.
 *
 * Es una foto, no una promesa: entre que se muestra un hueco y se lo retiene, otro se lo puede
 * ganar, y ahí contesta 409 `startHold`.
 *
 * Errores que la pantalla tiene que tratar aparte:
 * - **409**: el conector está fuera de servicio. Distinto de una lista vacía, que es "funciona y
 *   está todo tomado".
 * - **404**: el conector no existe.
 */
export async function getAvailability(
  connectorId: number,
  from: Date,
  to: Date,
  signal?: AbortSignal,
): Promise<FreeWindow[]> {
  const response = await api.get<FreeWindowResponse[]>('/bookings/availability', {
    params: { connectorId, from: from.toISOString(), to: to.toISOString() },
    signal,
  })
  return response.map((window) => ({ start: new Date(window.start), end: new Date(window.end) }))
}

/**
 * ECO-35: Procesa el checkout de una reserva a través del Facade en /api/checkout/booking.
 * Orquesta la verificación de tolerancia de gracia (RF09), vigencia del slot (RF08),
 * cálculo de seña (RF06) y verificación de medios de pago (RF02).
 */
export async function checkoutBooking(
  holdId: string,
  paymentMethodId?: number | null,
  acceptGracePeriod: boolean = true,
): Promise<Booking> {
  const response = await api.post<{
    bookingId: number
    connectorId: number
    start: string
    end: string
    status: BookingResponse['status']
    depositAmount: number
    paymentSummary: string
    checkedOutAt: string
  }>('/checkout/booking', {
    holdId,
    paymentMethodId: paymentMethodId ?? null,
    acceptGracePeriod,
  })

  const [booking] = await withLocations([
    {
      id: response.bookingId,
      connectorId: response.connectorId,
      start: response.start,
      end: response.end,
      status: response.status,
      createdAt: response.checkedOutAt,
    },
  ])
  return booking
}

/**
 * RF08: convierte la retención en una reserva guardada. Desde acá el conector queda bloqueado
 * para el resto durante esa ventana.
 *
 * Errores que la pantalla tiene que tratar aparte:
 * - **410**: la retención venció antes de confirmar. Hay que volver a elegir el horario.
 * - **404**: la retención no existe; típicamente porque el proceso de Reservas se reinició y las
 *   retenciones viven en memoria.
 * - **409**: mientras tanto se guardó otra reserva que se cruza.
 */
export async function confirmBooking(holdId: string): Promise<Booking> {
  const response = await api.post<BookingResponse>('/bookings', { holdId })
  const [booking] = await withLocations([response])
  return booking
}

/**
 * Las reservas del conductor que pregunta, de la más próxima a la más lejana.
 *
 * Trae también las canceladas y las que ya terminaron. Separar la activa del historial no es
 * asunto del backend ni de este archivo: ver `timeline.ts`.
 *
 * @param signal para cancelar la petición si la pantalla se desmonta antes de que responda.
 */
export async function listMyBookings(signal?: AbortSignal): Promise<Booking[]> {
  const response = await api.get<BookingResponse[]>('/bookings/mine', { signal })
  return withLocations(response)
}

/**
 * Cancela una reserva propia y libera su ventana. Responde 204 sin cuerpo.
 *
 * Es idempotente en el backend: cancelar una reserva ya cancelada no es un error. Si el pedido se
 * cortó, se puede mandar de nuevo sin preguntar antes cómo quedó.
 *
 * Errores que la pantalla tiene que tratar aparte:
 * - **400**: la ventana ya terminó y no hay nada que liberar.
 * - **404**: la reserva no existe.
 */
export function cancelBooking(bookingId: number): Promise<void> {
  return api.delete(`/bookings/${String(bookingId)}`)
}

/*
 * ---------------------------------------------------------------------------
 * Traducciones
 * ---------------------------------------------------------------------------
 */

function toHold(response: HoldResponse): Hold {
  return {
    id: response.id,
    connectorId: response.connectorId,
    start: new Date(response.start),
    end: new Date(response.end),
    expiresAt: new Date(response.expiresAt),
  }
}

/**
 * Convierte las reservas y les pone la ubicación de su conector.
 *
 * **Si la red no se puede traer, las reservas salen igual, sin ubicación.** Lo que el conductor
 * vino a ver es su reserva; que falte el nombre de la estación es peor que tenerlo, pero mucho
 * mejor que una pantalla de error que tampoco le deja cancelar.
 */
async function withLocations(responses: BookingResponse[]): Promise<Booking[]> {
  if (responses.length === 0) return []

  let locations = new Map<number, BookingLocation>()
  try {
    locations = indexByConnector(await fetchAllStations())
  } catch {
    // Sin red de estaciones: las reservas quedan con `location: null`. Ver arriba.
  }

  return responses.map((response) => ({
    id: response.id,
    connectorId: response.connectorId,
    start: new Date(response.start),
    end: new Date(response.end),
    status: response.status,
    createdAt: new Date(response.createdAt),
    location: locations.get(response.connectorId) ?? null,
  }))
}

/**
 * Un índice de conector a ubicación, armado sobre la red entera.
 *
 * Sirve porque `fetchAllStations` busca sin filtro de tipo ni de potencia y con
 * `onlyAvailable: false`, así que `matchingConnectors` trae todos los conectores de cada
 * estación, incluidos los ocupados y los fuera de servicio.
 */
function indexByConnector(stations: StationResult[]): Map<number, BookingLocation> {
  const index = new Map<number, BookingLocation>()
  for (const station of stations) {
    for (const connector of station.matchingConnectors) {
      index.set(connector.connectorId, {
        stationId: station.stationId,
        stationName: station.name,
        address: station.address,
        latitude: station.latitude,
        longitude: station.longitude,
        connectorType: connector.connectorType,
        maxPowerKw: connector.maxPowerKw,
      })
    }
  }
  return index
}
