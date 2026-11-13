/**
 * Tipos de la sesión de carga (RF11, ECO-44). Equivalen a los DTO de `SessionController` en
 * ecopedia-charging: `ActivationHttpRequest`, `SessionHttpResponse` y `SessionStopResponse`.
 */

/** Equivale al enum SessionStatus. */
export type SessionStatus = 'ACTIVE' | 'COMPLETED' | 'FAULTED'

/**
 * Cómo quedó el cobro del consumo. Equivale al enum ConsumptionPaymentStatus.
 *
 * - `NOT_REQUIRED`: el consumo no superó la seña, o no hubo consumo.
 * - `CHARGED`: se cobró la diferencia.
 * - `DEBT_RECORDED`: no se pudo cobrar y quedó como saldo pendiente.
 * - `PENDING_SETTLEMENT`: el cobro todavía no se resolvió.
 */
export type ConsumptionPaymentStatus =
  'NOT_REQUIRED' | 'CHARGED' | 'DEBT_RECORDED' | 'PENDING_SETTLEMENT'

/** Lo que se manda para iniciar la carga. */
export interface ActivationRequest {
  /** El PIN de la terminal o el contenido de su QR. Es lo que acredita que el conductor está ahí. */
  pinOrQr: string
  targetBatteryPercent: number
  /** La reserva con la que viene, si la eligió. Sin ella, el backend usa la suya si ya se puede. */
  bookingId?: number
}

/**
 * Una sesión, tal como la manda el backend. `GET /sessions/active`, `GET /sessions/{id}` y la
 * habilitación traen los datos de la carga; el stop trae los mismos nombres para la liquidación,
 * así que se leen con este mismo tipo. Los instantes llegan como texto ISO-8601.
 */
export interface SessionResponse {
  sessionId: string
  bookingId: number | null
  connectorId: number
  status: SessionStatus
  targetBatteryPercent?: number | null
  powerLimitKw?: number | null
  startedAt: string
  stoppedAt?: string | null
  kwhDelivered?: number | null
  currentPowerKw?: number | null
  batteryPercent?: number | null
  depositAmount?: number | null
  realCost?: number | null
  finalBilled?: number | null
  additionalCharge?: number | null
  paymentStatus?: ConsumptionPaymentStatus | null
}

/** La sesión con los instantes ya convertidos, que es como la usa la pantalla. */
export interface ChargingSession {
  sessionId: string
  bookingId: number | null
  connectorId: number
  status: SessionStatus
  targetBatteryPercent: number | null
  powerLimitKw: number | null
  startedAt: Date
  stoppedAt: Date | null
  kwhDelivered: number
  currentPowerKw: number | null
  batteryPercent: number | null
  depositAmount: number | null
  realCost: number | null
  finalBilled: number | null
  additionalCharge: number | null
  paymentStatus: ConsumptionPaymentStatus | null
}
