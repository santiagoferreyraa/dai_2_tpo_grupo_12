import { api } from '@/lib/api'

import type { ActivationRequest, ChargingSession, SessionResponse } from '../types'

/**
 * Las llamadas de la sesión de carga a ecopedia-charging (RF11, ECO-44). El proxy de Vite manda
 * `/api/sessions` al 8082.
 */

function toSession(response: SessionResponse): ChargingSession {
  return {
    sessionId: response.sessionId,
    bookingId: response.bookingId,
    connectorId: response.connectorId,
    status: response.status,
    targetBatteryPercent: response.targetBatteryPercent ?? null,
    powerLimitKw: response.powerLimitKw ?? null,
    startedAt: new Date(response.startedAt),
    stoppedAt: response.stoppedAt ? new Date(response.stoppedAt) : null,
    kwhDelivered: response.kwhDelivered ?? 0,
    currentPowerKw: response.currentPowerKw ?? null,
    batteryPercent: response.batteryPercent ?? null,
    depositAmount: response.depositAmount ?? null,
    realCost: response.realCost ?? null,
    finalBilled: response.finalBilled ?? null,
    additionalCharge: response.additionalCharge ?? null,
    paymentStatus: response.paymentStatus ?? null,
  }
}

/**
 * Escanear → validar → activar. Devuelve la sesión ya activa; los rechazos llegan como `ApiError`
 * con un `code` estable en el cuerpo (ver `activationErrorMessage`).
 */
export async function startCharging(request: ActivationRequest): Promise<ChargingSession> {
  return toSession(await api.post<SessionResponse>('/sessions/activation', request))
}

/** La sesión activa del conductor, o `null` si no está cargando (el backend contesta 204). */
export async function fetchActiveSession(signal?: AbortSignal): Promise<ChargingSession | null> {
  const response = await api.get<SessionResponse | undefined>('/sessions/active', { signal })
  return response ? toSession(response) : null
}

export async function fetchSession(
  sessionId: string,
  signal?: AbortSignal,
): Promise<ChargingSession> {
  return toSession(
    await api.get<SessionResponse>(`/sessions/${encodeURIComponent(sessionId)}`, { signal }),
  )
}

/**
 * Detiene la carga. Sin cuerpo: los kWh los dice el cargador. Repetirlo devuelve lo mismo, porque
 * el backend guarda la liquidación y no vuelve a cobrar.
 */
export async function stopCharging(sessionId: string): Promise<ChargingSession> {
  return toSession(
    await api.post<SessionResponse>(`/sessions/${encodeURIComponent(sessionId)}/stop`),
  )
}
