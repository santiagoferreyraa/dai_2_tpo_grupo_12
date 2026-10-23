import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'

import { useSession } from './session'
import type { Role } from './types'

interface RequireSessionProps {
  /** Si se indica, además de sesión hace falta uno de estos roles. */
  roles?: Role[]
  children: ReactNode
}

/**
 * Guard de las rutas privadas: sin sesión manda al login, y sin el rol a acceso denegado.
 *
 * <p><b>Esto es experiencia de usuario, no seguridad, y conviene tenerlo clarísimo para la
 * defensa.</b> Cualquiera puede saltear este componente tocando el estado del navegador. Lo
 * que protege de verdad son los `@PreAuthorize` del backend, que corren en el servidor y
 * rechazan el pedido aunque el front diga que sí —403 sin sesión, 404 con el rol equivocado—.
 * Este guard existe para que el usuario vea una
 * pantalla de login en vez de una aplicación donde cada botón falla sin explicación.
 */
export default function RequireSession({ roles, children }: RequireSessionProps) {
  const session = useSession()
  const location = useLocation()

  if (session === null) {
    /*
     * Se guarda a dónde quería ir para que el login lo devuelva ahí. `replace` evita que el
     * botón de atrás lo traiga de vuelta a una ruta que ya sabemos que va a rebotar.
     */
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (roles !== undefined && !roles.includes(session.role)) {
    /*
     * Con sesión pero sin el rol no se manda al login: pedirle que se autentique a alguien
     * que ya está autenticado es un lazo que no lleva a ninguna parte. Va a la pantalla de
     * acceso denegado, que además le da una salida.
     *
     * `replace` para que el botón de atrás no lo devuelva a la ruta prohibida, que lo
     * rebotaría acá de nuevo.
     */
    return <Navigate to="/forbidden" replace />
  }

  return children
}
