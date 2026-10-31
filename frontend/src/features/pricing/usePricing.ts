import { useEffect, useState } from 'react'

import { fetchNetworkRates, fetchStationPricing } from './data/pricingRepository'
import type { NetworkRates, StationPricing } from './types'

/**
 * Lecturas de Tarificación para los formularios.
 *
 * La respuesta se guarda junto con lo que se pidió, y si lo pedido cambia se lee como "cargando" sin
 * tocar el estado dentro del efecto (es lo que el linter pide, y lo mismo que hace `useDeposit`).
 */

export type Loaded<T> = { status: 'loading' } | { status: 'ready'; value: T } | { status: 'failed' }

/** Los precios de la red y las reglas con que se aplican. */
export function useNetworkRates(): Loaded<NetworkRates> {
  const [state, setState] = useState<Loaded<NetworkRates>>({ status: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    fetchNetworkRates(controller.signal).then(
      (value) => {
        if (!controller.signal.aborted) setState({ status: 'ready', value })
      },
      () => {
        if (!controller.signal.aborted) setState({ status: 'failed' })
      },
    )
    return () => controller.abort()
  }, [])

  return state
}

/** La seña y la hora pico de una estación; con `null` (una estación nueva) no pide nada. */
export function useStationPricing(stationId: number | null): Loaded<StationPricing> | null {
  const [answer, setAnswer] = useState<{ stationId: number; state: Loaded<StationPricing> } | null>(
    null,
  )

  useEffect(() => {
    if (stationId === null) return
    const controller = new AbortController()
    fetchStationPricing(stationId, controller.signal).then(
      (value) => {
        if (!controller.signal.aborted) setAnswer({ stationId, state: { status: 'ready', value } })
      },
      () => {
        if (!controller.signal.aborted) setAnswer({ stationId, state: { status: 'failed' } })
      },
    )
    return () => controller.abort()
  }, [stationId])

  if (stationId === null) return null
  return answer?.stationId === stationId ? answer.state : { status: 'loading' }
}

/** "18 a 22, ×1,2", para explicar la hora pico con los valores que de verdad están configurados. */
export function describePeak(rates: NetworkRates): string {
  const factor = String(rates.peakMultiplier).replace('.', ',')
  return `${String(rates.peakStartHour)} a ${String(rates.peakEndHour)} h, ×${factor}`
}
