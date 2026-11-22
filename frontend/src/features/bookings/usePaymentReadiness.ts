import { useEffect, useState } from 'react'

import { listDebts } from '@/features/payments/data/debtsRepository'
import { listCards } from '@/features/payments/data/paymentMethodsRepository'
import type { PaymentMethod } from '@/features/payments/types'

/**
 * Si el conductor tiene con qué pagar, preguntado al abrir el diálogo de reserva (RF02).
 *
 * - `checking`: la consulta está viajando.
 * - `ready`: tiene al menos una tarjeta, **o no se pudo saber**. Trae las tarjetas para elegir con
 *   cuál pagar; vacías cuando no se pudo saber, y entonces no se ofrece elegir.
 * - `missing`: Pagos contestó que no tiene ninguna.
 * - `debt`: tiene una deuda sin pagar (ECO-62). Gana sobre las tarjetas: con deuda el checkout
 *   rechaza aunque tenga diez, y lo que hay que hacer primero es pagarla.
 *
 * **Es un aviso, no el control.** Quien rechaza una reserva sin tarjeta es el checkout del
 * backend. Esto existe para que el conductor se entere antes de elegir horario y no después de
 * retener un conector diez minutos que no va a poder confirmar. Por eso, si la consulta falla, se
 * deja pasar: sin datos, se elige a ciegas y el backend contesta, igual que la disponibilidad. Con
 * la deuda es igual: si no se pudo saber, el 409 `DRIVER_HAS_DEBT` del checkout lo dice al confirmar.
 */
export type PaymentReadiness =
  | { status: 'checking' }
  | { status: 'ready'; cards: PaymentMethod[] }
  | { status: 'missing' }
  | { status: 'debt' }

export function usePaymentReadiness(): PaymentReadiness {
  const [readiness, setReadiness] = useState<PaymentReadiness>({ status: 'checking' })

  useEffect(() => {
    const controller = new AbortController()

    /* Las dos a la vez: ninguna espera a la otra, y una que falle no tapa a la otra. */
    void Promise.allSettled([listCards(controller.signal), listDebts(controller.signal)]).then(
      ([cards, debts]) => {
        if (controller.signal.aborted) return
        if (debts.status === 'fulfilled' && debts.value.length > 0) {
          setReadiness({ status: 'debt' })
        } else if (cards.status === 'rejected') {
          setReadiness({ status: 'ready', cards: [] })
        } else {
          setReadiness(
            cards.value.length > 0
              ? { status: 'ready', cards: cards.value }
              : { status: 'missing' },
          )
        }
      },
    )

    return () => controller.abort()
  }, [])

  return readiness
}
