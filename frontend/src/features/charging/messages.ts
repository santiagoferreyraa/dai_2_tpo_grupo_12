import { ApiError } from '@/lib/api'

import type { SessionStopReason } from './types'

/** El cuerpo de un error del backend: el `code` estable y, según el caso, algún dato más. */
interface ProblemBody {
  code?: unknown
  availableFrom?: unknown
}

function problemOf(error: unknown): ProblemBody | null {
  if (!(error instanceof ApiError)) return null
  const body = error.detail
  return body !== null && typeof body === 'object' ? (body as ProblemBody) : null
}

/** El `code` estable que manda el backend en el cuerpo de un error, si lo hay. */
export function errorCode(error: unknown): string | null {
  const code = problemOf(error)?.code
  return typeof code === 'string' ? code : null
}

const TIME = new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit' })

/**
 * Qué decirle al conductor cuando la carga no arranca (RF11, ECO-44).
 *
 * **Nunca se muestra el `detail` del backend**: el texto lo elige la pantalla según el `code`. El
 * `detail` está escrito para el log y para quien prueba la API, no para alguien parado frente al
 * cargador, y puede cambiar sin aviso.
 */
export function activationErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return 'No pudimos iniciar la carga. Probá de nuevo.'
  if (error.status === 0) {
    return 'No hay conexión con el servidor. Revisá tu conexión y probá de nuevo.'
  }

  switch (errorCode(error)) {
    case 'INVALID_ACCESS_CODE':
      return 'El código no corresponde a ningún conector. Revisá el PIN o volvé a escanear el QR de la terminal.'
    case 'TOO_MANY_ACCESS_CODE_ATTEMPTS':
      return 'Erraste el código demasiadas veces. Esperá unos minutos antes de volver a intentar.'
    case 'ACTIVATION_TOO_EARLY': {
      const raw = problemOf(error)?.availableFrom
      const from = typeof raw === 'string' ? new Date(raw) : null
      return from !== null && !Number.isNaN(from.getTime())
        ? `Todavía no podés empezar a cargar. Vas a poder desde las ${TIME.format(from)}.`
        : 'Todavía no podés empezar a cargar. Probá de nuevo más cerca de tu turno.'
    }
    case 'SLOT_UNAVAILABLE':
      return 'Esta terminal todavía está en uso. Probá de nuevo en unos minutos.'
    case 'BOOKING_STATE_CONFLICT':
      return 'Esta reserva ya no se puede usar para cargar.'
    case 'DRIVER_ALREADY_BOOKED':
      return 'Tenés una reserva en otro conector a esta hora. Usala o cancelala antes de cargar acá.'
    case 'PAYMENT_METHOD_INVALID':
      return 'No pudimos validar tu tarjeta, o tenés un saldo pendiente. Revisá tus medios de pago en el perfil.'
    case 'PAYMENT_CHECK_UNAVAILABLE':
      return 'No pudimos validar tu tarjeta en este momento. Probá de nuevo en unos minutos.'
    case 'CONNECTOR_NOT_BOOKABLE':
      return 'Este conector no puede cargar ahora. Probá con otro conector de la estación.'
    case 'CHARGER_REJECTED':
    case 'CHARGER_UNAVAILABLE':
      return 'El cargador no respondió. Si tenías reserva, te devolvemos la seña. Probá con otro conector.'
    case 'TERMINALS_UNAVAILABLE':
      return 'No pudimos verificar la terminal. Probá de nuevo en unos minutos.'
    case 'INVALID_REQUEST':
      return 'Esa reserva no es para este conector. Escaneá el QR del conector que reservaste.'
    default:
      break
  }
  if (error.status === 404) return 'No encontramos esa reserva o ese conector.'
  return 'No pudimos iniciar la carga. Probá de nuevo en unos minutos.'
}

/** Por qué terminó la carga, dicho para el conductor (ECO-45). */
export const STOP_REASON_LABEL: Record<SessionStopReason, string> = {
  DRIVER_STOPPED: 'La detuviste vos.',
  TARGET_REACHED: 'La batería llegó al porcentaje que elegiste.',
  WINDOW_ENDED: 'Se terminó tu tiempo disponible en la terminal.',
}
