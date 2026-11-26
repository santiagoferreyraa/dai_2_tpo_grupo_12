import { api } from '@/lib/api'

import type { AnalyticsResponse } from '../types'

/**
 * El tablero de Estadísticas (ECO-301). Vive en `ecopedia-async`, que lo arma con las franjas de
 * 15 minutos de la telemetría.
 *
 * Quién lo pide no viaja: sale del token. El administrador recibe la red entera y el operador,
 * solo sus estaciones.
 *
 * Errores con `code` que la pantalla trata aparte:
 * - **400 `INVALID_DATE_RANGE`**: el rango está invertido o pasa de 31 días.
 */
export function fetchAnalytics(
  from: Date,
  to: Date,
  signal?: AbortSignal,
): Promise<AnalyticsResponse> {
  return api.get<AnalyticsResponse>('/analytics', {
    params: { from: from.toISOString(), to: to.toISOString() },
    signal,
  })
}
