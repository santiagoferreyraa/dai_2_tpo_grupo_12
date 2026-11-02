import type { DepositStatus } from './types'

/**
 * Lo que se le cobra al conductor por una reserva: la seña al confirmar y, más adelante, la carga.
 *
 * **La seña sale del backend, nunca de acá** (ECO-53). La fija Tarificación por conector, y es la
 * misma que cobra el checkout: antes había un monto fijo escrito en este archivo, y la pantalla podía
 * decir un número mientras se cobraba otro. Antes de confirmar se pide a Tarificación (`useDeposit`);
 * después, viene con cada reserva.
 */

const MONEY = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  maximumFractionDigits: 0,
})

/** "$ 12.000". Sin centavos: ninguno de los montos que se muestran los tiene. */
export function formatMoney(amount: number): string {
  return MONEY.format(amount)
}

/**
 * Las horas de anticipación para que cancelar devuelva la seña, calculadas con lo que mandó el
 * backend: el inicio menos el límite. Así el número no está escrito en el front y no puede quedar
 * distinto del que aplica Reservas.
 */
export function refundNoticeHours(start: Date, refundableUntil: Date): number {
  return Math.round((start.getTime() - refundableUntil.getTime()) / 3_600_000)
}

/** "5 horas" o "1 hora". */
export function formatHours(hours: number): string {
  return hours === 1 ? '1 hora' : `${String(hours)} horas`
}

/**
 * Cómo terminó la seña, en palabras del conductor, para el historial. Se lee pegado al monto —"Seña
 * $ 8.000 · devuelta"—, y por eso no repite la palabra. Sin entrada cuando no hay nada que contar:
 * sin seña, o con la reserva todavía en pie.
 */
export const DEPOSIT_OUTCOME_LABEL: Partial<Record<DepositStatus, string>> = {
  REFUND_PENDING: 'devolución en curso',
  REFUNDED: 'devuelta',
  FORFEITED: 'no devuelta',
}
