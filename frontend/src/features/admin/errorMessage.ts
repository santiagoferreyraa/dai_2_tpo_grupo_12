import { ApiError } from '@/lib/api'

/**
 * El mensaje que ve el ADMIN cuando una acción de `/admin` falla: qué no se pudo hacer y qué hacer
 * ahora. Nunca el `message` del error, que es el `detail` del backend o un "Error 400" escrito para
 * el log (directiva 7, fijada por Manuel el 05/10).
 *
 * @param whatFailed la frase de la acción, terminada en punto: "No se pudo eliminar el conector."
 *
 * El error técnico va a la consola del navegador, para quien depure. 401 y 403 no llegan acá con
 * sentido propio: `api.ts` ya cierra la sesión ante esos dos.
 */
export function adminErrorMessage(error: unknown, whatFailed: string): string {
  console.error(whatFailed, error)

  if (error instanceof ApiError) {
    // En /admin un 404 es algo que otro borró o dio de baja mientras esta lista estaba abierta.
    if (error.status === 404) return `${whatFailed} Ya no existe: recargá la lista.`
    if (error.status === 400 || error.status === 422) {
      return `${whatFailed} Revisá los datos que cargaste.`
    }
  }
  // Sin conexión (status 0), 5xx o cualquier otro: lo único útil es volver a intentar.
  return `${whatFailed} Probá de nuevo en un momento.`
}
