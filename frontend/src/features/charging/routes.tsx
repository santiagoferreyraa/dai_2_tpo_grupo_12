import type { RouteObject } from 'react-router'

import RequireSession from '@/features/auth/RequireSession'

import ChargePage from './ChargePage'

/**
 * Rutas de la feature "charging": iniciar, seguir y detener la carga (RF11, ECO-44). Solo para
 * conductores: el operador y el ADMIN no cargan autos desde acá.
 */
export const chargingRoutes: RouteObject[] = [
  {
    path: 'charge',
    element: (
      <RequireSession roles={['CONDUCTOR']}>
        <ChargePage />
      </RequireSession>
    ),
  },
]
