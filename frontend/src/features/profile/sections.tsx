import type { Role } from '@/features/auth/types'
import ActiveBookingDot from '@/features/bookings/ActiveBookingDot'
import { BellIcon, CalendarIcon, CardIcon, SlidersIcon } from '@/features/navigation/icons'
import UnreadNotificationsDot from '@/features/notifications/UnreadNotificationsDot'

/**
 * Las secciones del perfil, en un solo lugar.
 *
 * Es el mismo criterio que `navigation/navSections.tsx` y por el mismo motivo: el riel de
 * escritorio y la cinta del celular son **dos disposiciones de la misma lista**, no dos listas.
 * Agregar una sección tiene que ser una línea acá y nada más.
 *
 * **"Perfil" ya no está en la lista, y es lo que cambió al llegar la cabecera.** Los datos del
 * usuario se ven ahora arriba de todo, en las cuatro secciones; dejarla también acá sería un
 * enlace a lo que ya se está mirando.
 *
 * **No se mezclan con las de la navegación principal.** Aquellas son los destinos de la
 * aplicación; estas viven adentro de uno de ellos, cambian el contenido de una sola pantalla y
 * no aparecen nunca en la franja de arriba ni en la barra del celular.
 */
export interface ProfileSection {
  to: string
  label: string
  Icon: (props: { className?: string }) => React.ReactElement
  /**
   * Qué roles ven esta sección. Sin esto, la ve cualquiera.
   *
   * Es la contracara del guard de la ruta: las tarjetas son del conductor y el backend contesta
   * 403 a cualquier otro rol, así que mostrarle el ícono a un operador es ofrecerle un camino
   * que termina en acceso denegado. **Esconder un enlace no es seguridad** y no pretende serlo:
   * quien escriba la dirección a mano llega igual, y ahí lo frena el guard. Es el mismo criterio
   * que el de la navegación principal; ver `navSections`.
   */
  roles?: Role[]
  /**
   * Un aviso sobre el ícono, o nada. Lo dibuja el riel y la cinta en la esquina del ícono, y decide
   * solo si se muestra: el de reservas titila únicamente con una reserva activa.
   *
   * Va acá y no escrito en el riel para que el riel no tenga que saber qué es una reserva.
   */
  Badge?: () => React.ReactElement | null
}

export const PROFILE_SECTIONS: ProfileSection[] = [
  {
    to: '/profile/reservations',
    label: 'Reservas e historial',
    Icon: CalendarIcon,
    Badge: ActiveBookingDot,
  },
  {
    to: '/profile/notifications',
    label: 'Notificaciones',
    Icon: BellIcon,
    Badge: UnreadNotificationsDot,
  },
  { to: '/profile/payment-methods', label: 'Medios de pago', Icon: CardIcon, roles: ['CONDUCTOR'] },
  { to: '/profile/settings', label: 'Configuración', Icon: SlidersIcon },
]

/** Las secciones que le corresponden a quien está mirando. Las dos barras leen de acá. */
export function visibleProfileSections(role: Role | null): ProfileSection[] {
  return PROFILE_SECTIONS.filter(
    (section) => section.roles === undefined || (role !== null && section.roles.includes(role)),
  )
}
