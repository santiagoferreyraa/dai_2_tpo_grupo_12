import { api } from '@/lib/api'

import type { UserProfile } from '../types'

/**
 * Las llamadas del frontend a `/api/users`: leer el propio perfil y editarlo.
 *
 * Están separadas de `authRepository` porque no son la misma familia de rutas: aquéllas son las
 * dos de `/api/auth` —entrar y darse de alta—, y éstas son sobre el usuario ya autenticado. El
 * criterio es el mismo que allá: ninguna pantalla escribe una ruta a mano.
 *
 * **Ninguna de las dos lleva el id del usuario, y no es un olvido**: el backend lo saca del
 * token, así que la edición solo puede alcanzar a quien la pide. Sin id en la dirección no hay
 * número que probar para caer en el perfil de otro.
 */

/**
 * El perfil del usuario del token: nombre real, rol y estado.
 *
 * No lleva parámetros a propósito. El backend resuelve de quién es el perfil a partir del token
 * —ver `UserController#getMyProfile`—, así que mandarle un id desde acá sería repetir un dato
 * que el servidor ya tiene y que además no le creería.
 */
export function fetchMyProfile(): Promise<UserProfile> {
  return api.get<UserProfile>('/users/profile')
}

/**
 * Cambia el nombre y el auto del usuario del token, y devuelve el perfil ya guardado.
 *
 * Se devuelve lo que quedó en la base y no lo que se mandó: el auto vuelve como ficha completa
 * —marca, modelo, conector, potencias— a partir del id que se mandó, así que la pantalla muestra
 * el resultado de verdad y no su propia suposición.
 *
 * **Los dos campos se mandan SIEMPRE, y con el auto eso importa.** El backend reemplaza lo
 * editable en vez de parcharlo: `vehicleModelId` en `null` quiere decir "sacate el auto", no
 * "dejalo como estaba". Es lo que hace posible quedarse sin auto sin inventar un centinela ni un
 * endpoint aparte para una sola columna, y es el motivo por el que este parámetro no es opcional:
 * olvidarlo borraría el vehículo en silencio. Ver `UpdateProfileRequest` en el backend.
 *
 * El nombre y el auto son lo ÚNICO editable, y la lista corta es una regla de negocio: el correo
 * identifica la cuenta y viaja en el token, y el rol es una decisión administrativa.
 */
export function updateMyProfile(
  fullName: string,
  vehicleModelId: number | null,
): Promise<UserProfile> {
  return api.put<UserProfile>('/users/profile', { fullName, vehicleModelId })
}

/**
 * Cambia la contraseña del usuario del token. No devuelve nada: el backend contesta 204.
 *
 * **Pide la actual además de la nueva, y eso es una regla de negocio, no un campo de más.** Un
 * token robado o una sesión abierta en una máquina ajena alcanzan para llegar hasta acá; la
 * contraseña vigente es lo único que distingue al dueño de quien pasaba por ahí. Ver
 * `ChangePasswordRequest` en el backend.
 *
 * La sesión abierta sigue valiendo después del cambio: el token ya emitido no depende de la
 * contraseña, así que no hay que volver a entrar.
 */
export function updateMyPassword(currentPassword: string, newPassword: string): Promise<void> {
  return api.put<void>('/users/profile/password', { currentPassword, newPassword })
}
