import { ApiError } from '@/lib/api'

import type { SessionEndReason } from './types'

/** El `code` estable que manda el backend en el cuerpo de un error, si lo hay. */
export function errorCode(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null
  const body = error.detail as { code?: unknown } | null
  return body !== null && typeof body === 'object' && typeof body.code === 'string'
    ? body.code
    : null
}

/**
 * Qué decirle al conductor cuando la carga no arranca (RF11, ECO-44).
 *
 * Casi siempre alcanza con el texto del backend, que ya está escrito para él. Se reescriben los que
 * piden una acción concreta en esta pantalla, y los que llegan sin texto útil.
 */
export function activationErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return 'No pudimos iniciar la carga. Probá de nuevo.'
  if (error.status === 0)
    return 'No hay conexión con el servidor. Revisá tu conexión y probá de nuevo.'

  switch (errorCode(error)) {
    case 'INVALID_ACCESS_CODE':
      return 'El código no corresponde a ningún conector. Revisá el PIN o volvé a escanear el QR de la terminal.'
    case 'TOO_MANY_ACCESS_CODE_ATTEMPTS':
      return 'Erraste el código demasiadas veces. Esperá unos minutos antes de volver a intentar.'
    case 'PAYMENT_METHOD_INVALID':
      return `${error.message}. Revisá tus medios de pago en el perfil.`
    case 'SLOT_UNAVAILABLE':
      return 'El conector está ocupado o hay una reserva por empezar. Probá con otro conector.'
    case 'DRIVER_ALREADY_BOOKED':
      return 'Tenés una reserva en curso en otro conector. Usala o cancelala antes de cargar acá.'
    default:
      break
  }
  if (error.status === 404) return 'No encontramos esa reserva o ese conector.'
  return error.message
}

export const END_REASON_LABEL: Record<SessionEndReason, string> = {
  DRIVER_STOPPED: 'La detuviste vos',
  TARGET_REACHED: 'Llegó al porcentaje que elegiste',
  WINDOW_ENDED: 'Terminó el tiempo disponible',
  BATTERY_FULL: 'La batería se llenó',
  FAULT: 'El cargador falló',
}
