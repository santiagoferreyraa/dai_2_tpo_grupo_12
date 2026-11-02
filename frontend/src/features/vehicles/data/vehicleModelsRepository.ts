import { api } from '@/lib/api'

import type { VehicleModel } from '../types'

/**
 * La única llamada de la feature: el catálogo de modelos.
 *
 * **No hay alta, ni baja, ni edición, y no es que falten.** El catálogo es cerrado: los modelos
 * entran por migración. Si se pudieran cargar desde una pantalla, el conductor que no encuentra
 * su auto terminaría inventando los números —y esos números son los que después filtran
 * estaciones y estiman cuánto tarda una carga—. Ver `VehicleService` en el backend.
 *
 * **Elegir el auto NO se hace acá**, aunque sea lo que uno esperaría de este archivo: el modelo
 * elegido es una propiedad del perfil, así que viaja en `PUT /api/users/profile` junto con el
 * nombre. Vive en `auth/data/userRepository`, con el resto del perfil.
 */
export function listVehicleModels(signal?: AbortSignal): Promise<VehicleModel[]> {
  return api.get<VehicleModel[]>('/vehicle-models', { signal })
}
