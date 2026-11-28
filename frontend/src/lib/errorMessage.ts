import { ApiError } from './api'

/**
 * Las palabras que ve el usuario cuando algo falla: qué no se pudo hacer y qué hacer ahora. Nunca
 * el `message` del error, que es el `detail` del backend o un "Error 502" escrito para el log
 * (directiva 6, fijada por Manuel el 05/10). Y sin pistas sobre el estado real (02/11): un 404 no
 * dice "ya no existe", dice qué hacer.
 *
 * @param whatFailed la frase de la acción, terminada en punto: "No se pudo guardar la tarjeta."
 * @param options frases propias para un `code` o un estado, completas: reemplazan a la genérica.
 *
 * El error técnico va a la consola del navegador, para quien depure. 401 y 403 no llegan acá con
 * sentido propio: `api.ts` ya cierra la sesión ante esos dos.
 */
export function errorMessage(
  error: unknown,
  whatFailed: string,
  options: {
    byCode?: Readonly<Record<string, string>>
    byStatus?: Readonly<Record<number, string>>
  } = {},
): string {
  console.error(whatFailed, error)

  if (error instanceof ApiError) {
    const code = problemCode(error)
    const forCode = code === null ? undefined : options.byCode?.[code]
    if (forCode !== undefined) return forCode
    const forStatus = options.byStatus?.[error.status]
    if (forStatus !== undefined) return forStatus

    if (error.status === 404) return `${whatFailed} Recargá la página y probá de nuevo.`
    if (error.status === 400 || error.status === 422) {
      return `${whatFailed} Revisá los datos que cargaste.`
    }
    if (error.status === 429) {
      return `${whatFailed} Hubo demasiados intentos. Esperá unos minutos y probá de nuevo.`
    }
  }
  // Sin conexión (status 0), 5xx o cualquier otro: lo único útil es volver a intentar.
  return `${whatFailed} Probá de nuevo en un momento.`
}

/** El `code` que el backend agrega a algunos rechazos, si vino uno. */
export function problemCode(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null
  const body = error.detail
  if (typeof body !== 'object' || body === null || !('code' in body)) return null
  return typeof body.code === 'string' ? body.code : null
}
