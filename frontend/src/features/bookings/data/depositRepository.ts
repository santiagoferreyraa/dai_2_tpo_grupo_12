import { api, ApiError } from '@/lib/api'

/**
 * La seña de un conector, preguntada a Tarificación antes de confirmar (RF06, ECO-53).
 *
 * Es la misma consulta que hace el checkout del lado del backend (`CoreTariffDirectory`), por la
 * ruta pública `/pricing/deposit`: lo que se muestra acá y lo que se cobra salen del mismo lugar.
 *
 * Devuelve:
 * - el monto, que puede ser 0 si el esquema no pide seña;
 * - **0 también si el conector no tiene esquema tarifario**: core lo contesta con un 4xx, y el
 *   checkout lo toma igual, como "sin seña";
 * - `null` si core no contestó, o contestó sin la seña: no se sabe, y la pantalla lo dice en vez de
 *   inventar un número.
 */
export async function fetchDeposit(
  connectorId: number,
  signal?: AbortSignal,
): Promise<number | null> {
  try {
    const answer = await api.get<{ depositAmount?: number | null }>('/pricing/deposit', {
      params: { connectorId },
      signal,
    })
    return typeof answer.depositAmount === 'number' ? answer.depositAmount : null
  } catch (error) {
    if (error instanceof ApiError && error.status >= 400 && error.status < 500) return 0
    return null
  }
}
