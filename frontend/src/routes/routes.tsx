import { createBrowserRouter } from 'react-router'

import App from '@/App'
import { adminRoutes } from '@/features/admin/routes'
import { authRoutes } from '@/features/auth/routes'
import { chargingRoutes } from '@/features/charging/routes'
import { contactRoutes } from '@/features/contact/routes'
import { homeRoutes } from '@/features/home/routes'
import { profileRoutes } from '@/features/profile/routes'
import { terminalRoutes } from '@/features/terminals/routes'

/**
 * El ÚNICO archivo compartido del ruteo.
 *
 * La regla: acá no se escriben rutas, se enchufan. Cada feature exporta su propio
 * arreglo de rutas desde src/features/<feature>/routes.tsx y acá se agrega una sola
 * línea. Si dos personas suman una feature el mismo día, el conflicto es de una línea
 * y se resuelve quedándose con las dos.
 *
 * Lo que NO hay que hacer es escribir el objeto de la ruta completo acá adentro: ahí
 * sí se pisan.
 */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      ...homeRoutes,
      ...authRoutes,
      ...terminalRoutes,
      ...chargingRoutes,
      ...contactRoutes,
      ...profileRoutes,
      ...adminRoutes,
      // ...bookingRoutes,
    ],
  },
])
