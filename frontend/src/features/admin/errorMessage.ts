import { ApiError } from '@/lib/api'
import { errorMessage, problemCode } from '@/lib/errorMessage'

/**
 * El mensaje que ve el ADMIN cuando una acción de `/admin` falla: qué no se pudo hacer y qué hacer
 * ahora. Es el de `lib/errorMessage` con las dos frases propias del backoffice.
 *
 * @param whatFailed la frase de la acción, terminada en punto: "No se pudo eliminar el conector."
 */
export function adminErrorMessage(error: unknown, whatFailed: string): string {
  return errorMessage(error, whatFailed, {
    byCode: {
      ADMIN_PROTECTED: `${whatFailed} Un administrador no se puede dar de baja ni cambiar de rol desde acá.`,
    },
    /*
     * En /admin un 404 suele ser algo que otro borró mientras la lista estaba abierta, pero no se
     * dice: el mensaje no da pistas sobre el estado real (Manuel, 02/11). Solo qué hacer.
     */
    byStatus: { 404: `${whatFailed} Recargá la lista y probá de nuevo.` },
  })
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
