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
    if (problemCode(error) === 'ADMIN_PROTECTED') {
      return `${whatFailed} Un administrador no se puede dar de baja ni cambiar de rol desde acá.`
    }
    // En /admin un 404 es algo que otro borró o dio de baja mientras esta lista estaba abierta.
    if (error.status === 404) return `${whatFailed} Ya no existe: recargá la lista.`
    if (error.status === 400 || error.status === 422) {
      return `${whatFailed} Revisá los datos que cargaste.`
    }
  }
  // Sin conexión (status 0), 5xx o cualquier otro: lo único útil es volver a intentar.
  return `${whatFailed} Probá de nuevo en un momento.`
}

/**
 * El error de la contraseña de confirmación, para mostrarlo adentro del diálogo, o `null` si el
 * error es de otra clase (y entonces se cierra el diálogo y va al aviso de la página).
 */
export function confirmationErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null
  if (problemCode(error) === 'PASSWORD_INCORRECT') return 'La contraseña no es correcta.'
  if (error.status === 429) {
    return 'Hubo demasiados intentos con una contraseña equivocada. Esperá unos minutos y probá de nuevo.'
  }
  return null
}

/** El `code` que el backend agrega a algunos rechazos, si vino uno. */
function problemCode(error: ApiError): string | null {
  const body = error.detail
  if (typeof body !== 'object' || body === null || !('code' in body)) return null
  return typeof body.code === 'string' ? body.code : null
}
