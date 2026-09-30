/**
 * Tipos de la feature Auth.
 *
 * Espejan los records de la capa de negocio del backend (com.ecopedia.core.user.domain).
 * Los nombres de campo son los que viajan en el JSON, así que van tal cual salen de Jackson.
 */

import type { VehicleModel } from '@/features/vehicles/types'

/**
 * Los roles con el nombre que viaja en el token, que es el del enum Java.
 *
 * Ojo con la asimetría: ARQUITECTURA_ECOPEDIA.md nombra estos roles `DRIVER`, `OPERATOR` y
 * `ADMIN`, y el código dice `CONDUCTOR`, `CPO` y `ADMIN`. La divergencia es una decisión
 * tomada, no un descuido — pero acá manda el backend, porque esto es el contrato del JSON.
 */
export type Role = 'CONDUCTOR' | 'CPO' | 'ADMIN'

/** Lo que pide el formulario de login. Equivale a Credentials. */
export interface Credentials {
  email: string
  password: string
}

/**
 * Lo que pide el formulario de registro. Equivale a RegisterRequest.
 *
 * **No lleva rol, y es a propósito.** El alta es pública, así que aceptar el rol por
 * parámetro dejaría que cualquiera se emitiera una cuenta de administrador. Todo el que se
 * registra nace `CONDUCTOR`.
 */
export interface RegistrationInput {
  email: string
  password: string
  fullName: string
}

/** La respuesta del login. Equivale a AuthResponse. */
export interface AuthResponse {
  token: string
  userId: number
  email: string
  role: Role
  expiresInSeconds: number
}

/** El perfil que devuelve el registro. Equivale a UserProfileResponse. */
export interface UserProfile {
  id: number
  email: string
  fullName: string
  role: Role
  active: boolean
  createdAt: string
  /**
   * El auto elegido, o `null` si todavía no eligió ninguno.
   *
   * **Viene la ficha entera y no el id**, y eso es deliberado del lado del backend: la portada
   * necesita el conector y las potencias apenas abre —para contar estaciones compatibles y
   * estimar cuánto tarda una carga—, y con un id suelto tendría que pedir el catálogo entero
   * para traducirlo. Ver `UserProfileResponse` en el backend.
   */
  vehicle: VehicleModel | null
}

/**
 * La sesión tal como la guarda el front.
 *
 * Es `AuthResponse` con el vencimiento resuelto a un instante absoluto en vez de una
 * duración: guardar "24 horas" no sirve para saber si un token rehidratado tres días después
 * sigue valiendo, y guardar el instante sí.
 *
 * Lleva además el nombre, que `AuthResponse` no trae: lo devuelve `GET /users/profile`, que es
 * otra llamada. Ver `fullName`.
 */
export interface Session {
  token: string
  userId: number
  email: string
  role: Role
  expiresAt: number
  /**
   * El auto del conductor, o `null` mientras no se sepa cuál es.
   *
   * Llega con el nombre, de la misma llamada y por el mismo motivo: el login no lo trae. Ver
   * `fullName`, que explica la espera.
   *
   * **`null` es ambiguo acá a propósito**: significa "no eligió auto" y también "todavía no
   * llegó el perfil". Distinguirlos pediría un tercer estado que ninguna pantalla usaría: las
   * dos situaciones se dibujan igual —sin ficha— y la segunda dura un instante.
   */
  vehicle: VehicleModel | null
  /**
   * El nombre real, o `null` mientras no se sepa.
   *
   * **Es `null` y no una cadena vacía porque son dos cosas distintas**: vacío sería "no tiene
   * nombre", y lo que pasa acá es "todavía no llegó". El login no lo trae, así que entre que se
   * abre la sesión y contesta el perfil hay un rato —y si esa llamada falla, nunca llega— en el
   * que hay que mostrar algo igual. De eso se ocupa `displayNameOf`.
   */
  fullName: string | null
}
