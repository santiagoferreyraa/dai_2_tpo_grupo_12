/**
 * Tipos de la sesión de carga (RF11, ECO-44). Equivalen a los DTO de `SessionController` en
 * ecopedia-charging: `ActivationHttpRequest`, `ActivationHttpResponse` y `SessionHttpResponse`.
 */

/** Equivale al enum SessionStatus. */
export type SessionStatus = 'ACTIVE' | 'COMPLETED' | 'FAULTED'

/** Por qué terminó. Equivale al enum SessionEndReason. */
export type SessionEndReason =
  'DRIVER_STOPPED' | 'TARGET_REACHED' | 'WINDOW_ENDED' | 'BATTERY_FULL' | 'FAULT'

/**
 * Cómo quedó el cobro del consumo. Equivale al enum SessionPaymentStatus.
 *
 * - `NOT_SETTLED`: la sesión sigue activa.
 * - `NOT_REQUIRED`: el consumo no superó la seña, o no hubo consumo.
 * - `CHARGED`: se cobró la diferencia.
 * - `DEBT_REGISTERED`: no se pudo cobrar y quedó como saldo pendiente.
 * - `PENDING`: el cobro todavía no se resolvió.
 */
export type SessionPaymentStatus =
  'NOT_SETTLED' | 'NOT_REQUIRED' | 'CHARGED' | 'DEBT_REGISTERED' | 'PENDING'

/** Lo que se manda para iniciar la carga. */
export interface ActivationRequest {
  /** El PIN de la terminal o el contenido de su QR. Es lo que acredita que el conductor está ahí. */
  pinOrQr: string
  targetBatteryPercent: number
  /** La reserva con la que viene, si la eligió. Sin ella, el backend usa la suya si es la hora. */
  bookingId?: number
}

/** Una sesión, tal como la manda el backend. Los instantes llegan como texto ISO-8601. */
export interface SessionResponse {
  sessionId: string
  bookingId: number | null
  connectorId: number
  status: SessionStatus
  targetBatteryPercent: number
  powerLimitKw: number
  startedAt: string
  stoppedAt?: string | null
  kwhDelivered?: number | null
  batteryPercent?: number | null
  endReason?: SessionEndReason | null
  realCost?: number | null
  depositApplied?: number | null
  finalAmount?: number | null
  amountToCharge?: number | null
  paymentStatus?: SessionPaymentStatus | null
}

/** La sesión con los instantes ya convertidos, que es como la usa la pantalla. */
export interface ChargingSession {
  sessionId: string
  bookingId: number | null
  connectorId: number
  status: SessionStatus
  targetBatteryPercent: number
  powerLimitKw: number
  startedAt: Date
  stoppedAt: Date | null
  kwhDelivered: number
  batteryPercent: number | null
  endReason: SessionEndReason | null
  realCost: number | null
  depositApplied: number | null
  finalAmount: number | null
  amountToCharge: number | null
  paymentStatus: SessionPaymentStatus
}
