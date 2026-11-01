import { api } from '@/lib/api'

import type { NetworkRates, NetworkRatesInput, StationPricing, StationPricingInput } from '../types'

/**
 * Acceso a Tarificación (`/api/pricing`, en core). Lo usan dos pantallas, `/admin` y `/stations`, y por
 * eso vive en su propia feature y no adentro de una de ellas.
 *
 * Leer es público; cambiar los precios de la red es solo del ADMIN, y la seña y la hora pico de una
 * estación, del ADMIN o del CPO dueño. Con otro rol el backend contesta 404 (SEC-05).
 */

export function fetchNetworkRates(signal?: AbortSignal): Promise<NetworkRates> {
  return api.get<NetworkRates>('/pricing/rates', { signal })
}

export function saveNetworkRates(input: NetworkRatesInput): Promise<NetworkRates> {
  return api.put<NetworkRates>('/pricing/rates', input)
}

/** La de una estación; si nadie la configuró, la de omisión: sin seña y con hora pico. */
export function fetchStationPricing(
  stationId: number,
  signal?: AbortSignal,
): Promise<StationPricing> {
  return api.get<StationPricing>(`/pricing/stations/${String(stationId)}`, { signal })
}

export function saveStationPricing(
  stationId: number,
  input: StationPricingInput,
): Promise<StationPricing> {
  return api.put<StationPricing>(`/pricing/stations/${String(stationId)}`, input)
}
