import type { RouteObject } from 'react-router'

import LoginPage from './LoginPage'
import NotFoundPage from './NotFoundPage'
import RegisterPage from './RegisterPage'

/**
 * Rutas de la feature "auth". Las tres son públicas, por definición: son la puerta.
 *
 * El comodín `*` está acá y no en la feature que lo usa porque no es de ninguna: la misma
 * pantalla la dibuja el guard, que vive en esta carpeta, cuando falta el rol. React Router
 * elige la ruta más específica, así que el comodín solo atrapa lo que ninguna otra reconoce,
 * sin importar en qué lugar del arreglo esté.
 */
export const authRoutes: RouteObject[] = [
  { path: 'login', element: <LoginPage /> },
  { path: 'register', element: <RegisterPage /> },
  { path: '*', element: <NotFoundPage /> },
]
