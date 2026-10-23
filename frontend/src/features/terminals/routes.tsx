import type { RouteObject } from 'react-router'

import RequireSession from '@/features/auth/RequireSession'

import StationsMapPage from './StationsMapPage'
import StationsPage from './StationsPage'

/**
 * Rutas de la feature "terminals".
 *
 * Se declaran acá, no en routes/routes.tsx: agregar una pantalla a esta feature no tiene
 * que tocar ningún archivo compartido. Ver src/README.md.
 *
 * Son dos pantallas sobre las mismas estaciones y con dos tareas distintas: `/stations` es
 * el ABM del operador (RF04, RF05) y `/stations/map` es la búsqueda del conductor (RF07).
 * El mapa cuelga del ABM y no de una ruta suelta porque es la misma feature vista de otro
 * modo, y así la URL lo dice.
 */
/*
 * El ABM es del operador, así que va detrás del guard y además con el rol exigido. Sin el
 * rol, un conductor con sesión entraría a una pantalla donde el listado carga —esa lectura es
 * pública— y después cada botón devuelve 404; por eso el guard lo desvía a la pantalla de acceso
 * denegado antes de que este ABM llegue a dibujarse, en vez de dejarlo mirando uno que no puede usar.
 *
 * El backend deja al administrador dar de baja una estación (RF03), pero no crear ni editar,
 * así que esta pantalla sigue siendo solo del operador: darle entrada al administrador sería
 * mostrarle un formulario que no puede guardar. Su lugar es el backoffice de ECO-27.
 *
 * El mapa queda FUERA del guard a propósito: es la pantalla del conductor y su búsqueda es
 * pública, así que pedir sesión para mirar dónde hay un cargador sería cerrar la puerta de
 * entrada del producto. Reservar sí va a pedir sesión, y eso lo resuelve Reservas.
 */
export const terminalRoutes: RouteObject[] = [
  {
    path: 'stations',
    element: (
      <RequireSession roles={['CPO']}>
        <StationsPage />
      </RequireSession>
    ),
  },
  { path: 'stations/map', element: <StationsMapPage /> },
]
