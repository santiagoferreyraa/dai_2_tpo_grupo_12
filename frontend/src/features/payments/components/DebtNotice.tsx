import { useCallback, useEffect, useState } from 'react'

import { ApiError } from '@/lib/api'

import { listDebts, payDebt } from '../data/debtsRepository'
import { describeDebt, formatDebtAmount } from '../debts'
import type { Debt, PaymentMethod } from '../types'

import PayDebtDialog from './PayDebtDialog'

/**
 * La `Idempotency-Key` del pago que quedó sin respuesta clara (QA-29), con la misma regla que el
 * checkout (`useBookingFlow`): **se reusa solo cuando no se sabe qué pasó** —la red se cortó o el
 * servidor no contestó—, para que el reintento averigüe si se cobró sin cobrar de nuevo. Con una
 * respuesta clara, o con otra deuda u otra tarjeta, va una clave nueva.
 *
 * Vive fuera del componente para que cerrar el diálogo después de un corte y volver a pagar siga
 * siendo el mismo intento.
 */
let uncertainAttempt: { debtId: number; cardId: number; key: string } | null = null

function attemptKey(debtId: number, cardId: number): string {
  if (uncertainAttempt?.debtId === debtId && uncertainAttempt.cardId === cardId) {
    return uncertainAttempt.key
  }
  return newIdempotencyKey()
}

function isUncertain(reason: unknown): boolean {
  return reason instanceof ApiError && (reason.status === 0 || reason.status >= 500)
}

/** `crypto.randomUUID` existe solo en contextos seguros; si no, `getRandomValues`. */
function newIdempotencyKey(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

/**
 * Las deudas del conductor arriba de sus tarjetas, con "Pagar ahora" (ECO-62).
 *
 * **Sin deuda no dibuja nada**: la pantalla de Medios de pago queda como siempre.
 *
 * **No dice en qué estado está la deuda.** Que se siga reintentando o que la cuenta haya quedado
 * marcada es lo que pasa por detrás; para el conductor es siempre lo mismo: qué debe, de qué carga
 * y que hasta pagarlo no puede reservar ni cargar (directiva 6). Por eso tampoco muestra la hora
 * del próximo reintento.
 *
 * Recibe las tarjetas de la pantalla en vez de pedirlas: son las mismas, y así una tarjeta recién
 * cargada aparece en el diálogo sin otra llamada.
 */
export default function DebtNotice({ cards }: { cards: PaymentMethod[] }) {
  const [debts, setDebts] = useState<Debt[]>([])
  const [loadFailed, setLoadFailed] = useState(false)
  const [reloadToken, setReloadToken] = useState(0)

  const [paying, setPaying] = useState<Debt | null>(null)
  const [busy, setBusy] = useState(false)
  const [payError, setPayError] = useState<string | null>(null)
  const [paidMessage, setPaidMessage] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    listDebts(controller.signal)
      .then((loaded) => {
        setDebts(loaded)
        setLoadFailed(false)
      })
      .catch(() => {
        if (!controller.signal.aborted) setLoadFailed(true)
      })
    return () => controller.abort()
  }, [reloadToken])

  const closeDialog = useCallback(() => {
    setPaying(null)
    setPayError(null)
  }, [])

  async function handlePay(debt: Debt, cardId: number) {
    const key = attemptKey(debt.id, cardId)
    setBusy(true)
    setPayError(null)
    try {
      await payDebt(debt.id, cardId, key)
      uncertainAttempt = null
      setPaying(null)
      setPaidMessage(`Listo, pagaste la ${describeDebt(debt).toLowerCase()}. Ya podés reservar.`)
      setReloadToken((token) => token + 1)
    } catch (reason) {
      uncertainAttempt = isUncertain(reason) ? { debtId: debt.id, cardId, key } : null
      setPayError(payErrorMessage(reason))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      {paidMessage !== null && (
        <p
          className="border-primary/40 bg-primary/10 text-text rounded-xl border p-4 text-sm"
          role="status"
        >
          {paidMessage}
        </p>
      )}

      {loadFailed && (
        <p className="text-text-muted text-sm" role="status">
          No pudimos revisar si tenés pagos pendientes. Recargá la página para ver el estado de tu
          cuenta.
        </p>
      )}

      {debts.length > 0 && (
        <div
          className="border-danger/40 bg-danger/10 flex flex-col gap-3 rounded-xl border p-4 text-sm"
          role="status"
        >
          <p className="text-text font-semibold">
            {debts.length > 1 ? 'Tenés pagos pendientes' : 'Tenés un pago pendiente'}
          </p>

          <ul className="flex flex-col gap-3">
            {debts.map((debt) => (
              <li key={debt.id} className="flex flex-wrap items-center justify-between gap-3">
                <span className="text-text">
                  {describeDebt(debt)} ·{' '}
                  <span className="font-semibold">{formatDebtAmount(debt.amount)}</span>
                </span>
                <button
                  type="button"
                  onClick={() => {
                    setPaidMessage(null)
                    setPaying(debt)
                  }}
                  className="bg-primary hover:bg-primary-strong text-on-primary rounded-lg px-4 py-2 text-sm font-semibold"
                >
                  Pagar ahora
                </button>
              </li>
            ))}
          </ul>

          <p className="text-text-muted">Mientras no lo pagues, no podés reservar ni cargar.</p>
        </div>
      )}

      {/* Uno nuevo cada vez que se abre: la tarjeta tocada en un pago no pasa al siguiente. */}
      <PayDebtDialog
        key={paying === null ? 'closed' : paying.id}
        debt={paying}
        cards={cards}
        onPay={(debt, cardId) => void handlePay(debt, cardId)}
        onCancel={closeDialog}
        busy={busy}
        error={payError}
      />
    </>
  )
}

/** El problema del pago, en palabras del conductor: nunca el código ni el texto del backend. */
function payErrorMessage(reason: unknown): string {
  const reload = 'Recargá la página y probá de nuevo.'
  if (!(reason instanceof ApiError)) return reload

  switch (problemCode(reason)) {
    case 'CARD_DECLINED':
      return 'No pudimos cobrar con esa tarjeta. Probá con otra o cargá una nueva.'
    case 'PAYMENT_METHOD_INVALID':
      return 'Esa tarjeta ya no se puede usar. Elegí otra o cargá una nueva.'
    case 'DEBT_PAYMENT_IN_PROGRESS':
      return 'Esperá un minuto y probá de nuevo.'
  }

  if (reason.status === 0 || reason.status >= 500) {
    return 'No pudimos procesar el pago ahora. Probá de nuevo en un momento: no te vamos a cobrar dos veces.'
  }
  return reload
}

function problemCode(reason: ApiError): string | null {
  const body = reason.detail
  if (typeof body !== 'object' || body === null || !('code' in body)) return null
  return typeof body.code === 'string' ? body.code : null
}
