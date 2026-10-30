import { useEffect, useState } from 'react'

import { listCards } from '@/features/payments/data/paymentMethodsRepository'
import type { PaymentMethod } from '@/features/payments/types'

/**
 * Si el conductor tiene con qué pagar, preguntado al abrir el diálogo de reserva (RF02).
 *
 * - `checking`: la consulta está viajando.
 * - `ready`: tiene al menos una tarjeta, **o no se pudo saber**. Trae las tarjetas para elegir con
 *   cuál pagar; vacías cuando no se pudo saber, y entonces no se ofrece elegir.
 * - `missing`: Pagos contestó que no tiene ninguna.
 *
 * **Es un aviso, no el control.** Quien rechaza una reserva sin tarjeta es el checkout del
 * backend. Esto existe para que el conductor se entere antes de elegir horario y no después de
 * retener un conector diez minutos que no va a poder confirmar. Por eso, si la consulta falla, se
 * deja pasar: sin datos, se elige a ciegas y el backend contesta, igual que la disponibilidad.
 */
export type PaymentReadiness =
  { status: 'checking' } | { status: 'ready'; cards: PaymentMethod[] } | { status: 'missing' }

export function usePaymentReadiness(): PaymentReadiness {
  const [readiness, setReadiness] = useState<PaymentReadiness>({ status: 'checking' })

  useEffect(() => {
    const controller = new AbortController()

    listCards(controller.signal).then(
      (cards) =>
        setReadiness(cards.length > 0 ? { status: 'ready', cards } : { status: 'missing' }),
      () => {
        if (controller.signal.aborted) return
        setReadiness({ status: 'ready', cards: [] })
      },
    )

    return () => controller.abort()
  }, [])

  return readiness
}
