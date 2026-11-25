import type { RouteObject } from 'react-router'

import RequireSession from '@/features/auth/RequireSession'

import AnalyticsPage from './AnalyticsPage'

/**
 * Rutas de la feature Estadísticas (ECO-301).
 *
 * Una sola pantalla para el administrador y el operador: el backend decide qué ve cada uno (la red
 * entera o sus estaciones), así que no hace falta una versión por rol. El conductor no entra: el
 * guard lo manda a acceso denegado, y la API le contestaría 404 igual.
 */
export const analyticsRoutes: RouteObject[] = [
  {
    path: 'analytics',
    element: (
      <RequireSession roles={['ADMIN', 'CPO']}>
        <AnalyticsPage />
      </RequireSession>
    ),
  },
]
