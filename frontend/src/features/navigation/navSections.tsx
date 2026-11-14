import type { Role } from '@/features/auth/types'

import {
  BoltIcon,
  ContactIcon,
  HomeIcon,
  KeyIcon,
  MapIcon,
  ProfileIcon,
  StationsIcon,
} from './icons'

/**
 * Las secciones de la navegación, en un solo lugar.
 *
 * Las dos barras —la de escritorio y la del celular— leen de acá, y esa es la razón de que
 * este archivo exista: son dos disposiciones distintas de **la misma** navegación, no dos
 * navegaciones. Agregar una sección tiene que ser una línea, no dos que se pueden olvidar de
 * acompañarse.
 */

export interface NavSection {
  to: string
  label: string
  Icon: (props: { className?: string }) => React.ReactElement
  /**
   * Qué roles ven esta sección. Sin esto, la ve cualquiera.
   *
   * Es la contracara del guard de la ruta: el ABM de estaciones exige rol de operador, así que
   * mostrarle el enlace a un conductor es ofrecerle un camino que termina en acceso denegado.
   *
   * **Esconder un enlace no es seguridad** y no pretende serlo: quien escriba la dirección a mano
   * llega igual, y ahí lo frena el guard —y al backend, la anotación sobre el método—. Lo que
   * evita es prometer algo que no se puede cumplir.
   */
  roles?: Role[]
  /**
   * Si la ruta solo marca activo cuando coincide exacta.
   *
   * Hace falta en `/` y en `/stations`, que son prefijo de otras: por omisión `NavLink`
   * considera activo todo prefijo de la ruta actual, así que sin esto la home quedaría
   * marcada en todas las pantallas y Estaciones se encendería mirando el mapa.
   */
  end?: boolean
}

/**
 * Las secciones del centro en escritorio.
 */
export const MAIN_SECTIONS: NavSection[] = [
  { to: '/', label: 'Inicio', Icon: HomeIcon, end: true },
  { to: '/stations', label: 'Estaciones', Icon: StationsIcon, end: true, roles: ['CPO'] },
  { to: '/stations/map', label: 'Mapa', Icon: MapIcon },
  /* Iniciar, seguir y detener la carga (ECO-44). Solo el conductor carga autos. */
  { to: '/charge', label: 'Cargar', Icon: BoltIcon, roles: ['CONDUCTOR'] },
  { to: '/contact', label: 'Contacto', Icon: ContactIcon },
  { to: '/admin', label: 'Backoffice', Icon: KeyIcon, roles: ['ADMIN'] },
]

/**
 * El perfil, aparte de las otras cuatro.
 *
 * Es la misma sección en las dos resoluciones, pero **no se dibuja igual**: en el celular es
 * el quinto ícono de la barra y en escritorio es la ficha del usuario a la derecha, con su
 * nombre. Separarlo acá es lo que permite que cada barra lo trate como necesita sin que la
 * lista del centro tenga un caso especial adentro.
 */
export const PROFILE_SECTION: NavSection = { to: '/profile', label: 'Perfil', Icon: ProfileIcon }

export const BASE_MOBILE_SECTIONS: NavSection[] = [
  { to: '/', label: 'Inicio', Icon: HomeIcon, end: true },
  { to: '/stations', label: 'Estaciones', Icon: StationsIcon, end: true, roles: ['CPO'] },
  { to: '/stations/map', label: 'Mapa', Icon: MapIcon },
  /* Iniciar, seguir y detener la carga (ECO-44). Solo el conductor carga autos. */
  { to: '/charge', label: 'Cargar', Icon: BoltIcon, roles: ['CONDUCTOR'] },
  { to: '/contact', label: 'Contacto', Icon: ContactIcon },
]

/** Las secciones, en el orden en que aparecen en la barra del celular. */
export const MOBILE_SECTIONS: NavSection[] = [...BASE_MOBILE_SECTIONS, PROFILE_SECTION]

/**
 * Si la sección está activa, con la misma regla que aplica `NavLink`.
 *
 * Existe porque las dos barras necesitan saberlo **afuera** de cada enlace: el estado activo
 * que `NavLink` calcula vive adentro del enlace, y tanto el círculo del celular como el
 * subrayado de escritorio tienen que conocer cuál es la sección activa antes de dibujar la
 * lista, no durante.
 */
export function isSectionActive(section: NavSection, pathname: string): boolean {
  if (section.end === true) return pathname === section.to
  return pathname === section.to || pathname.startsWith(`${section.to}/`)
}

/**
 * Las secciones que le corresponden a quien está mirando.
 *
 * Se filtra en un solo lugar y las dos barras lo usan: si cada una repitiera la condición, la del
 * celular podría terminar mostrando una sección que la de escritorio esconde. En el celular
 * además importa doble, porque de la cantidad de secciones sale el ancho de cada una.
 */
export function visibleSections(sections: NavSection[], role: Role | null): NavSection[] {
  return sections.filter(
    (section) => section.roles === undefined || (role !== null && section.roles.includes(role)),
  )
}
