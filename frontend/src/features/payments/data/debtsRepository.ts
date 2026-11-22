import { api } from '@/lib/api'

import type { Debt } from '../types'

/**
 * Las deudas del conductor (ECO-62): listarlas y pagar una. Viven en Pagos, en
 * `ecopedia-integration`, como las tarjetas: ver `paymentMethodsRepository`.
 *
 * El conductor no viaja en ninguna de las dos: sale del token.
 */

/** Las deudas sin pagar del conductor. Vacío si no debe nada. */
export function listDebts(signal?: AbortSignal): Promise<Debt[]> {
  return api.get<Debt[]>('/payment-debts', { signal })
}

/**
 * Paga una deuda con la tarjeta elegida.
 *
 * Lleva la `Idempotency-Key`, como el checkout: la misma en cada reintento del mismo pago, para que
 * una respuesta perdida no termine en dos cobros. Quién la genera y cuándo la reusa lo decide
 * `DebtNotice`.
 *
 * Errores que la pantalla trata aparte, todos con `code`:
 * - **402 `CARD_DECLINED`**: la pasarela rechazó la tarjeta.
 * - **422 `PAYMENT_METHOD_INVALID`**: la tarjeta ya no sirve.
 * - **409 `DEBT_PAYMENT_IN_PROGRESS`**: el barrido está cobrando esa misma deuda en este momento.
 * - **503 `PAYMENT_GATEWAY_UNAVAILABLE`**: la pasarela no contestó.
 */
export function payDebt(
  debtId: number,
  paymentMethodId: number,
  idempotencyKey: string,
): Promise<Debt> {
  return api.post<Debt>(
    `/payment-debts/${String(debtId)}/payment`,
    { paymentMethodId },
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
}
