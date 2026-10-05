import { useEffect, useState } from 'react'

import { listCards } from '@/features/payments/data/paymentMethodsRepository'

/**
 * Si el conductor tiene con qué pagar, preguntado al abrir el diálogo de reserva (RF02).
 *
 * - `checking`: la consulta está viajando.
 * - `ready`: tiene al menos una tarjeta, **o no se pudo saber**.
 * - `missing`: Pagos contestó que no tiene ninguna.
 *
 * **Es un aviso, no el control.** Quien rechaza una reserva sin tarjeta es el checkout del
 * backend. Esto existe para que el conductor se entere antes de elegir horario y no después de
 * retener un conector diez minutos que no va a poder confirmar. Por eso, si la consulta falla, se
 * deja pasar: sin datos, se elige a ciegas y el backend contesta, igual que la disponibilidad.
 */
export type PaymentReadiness = 'checking' | 'ready' | 'missing'

export function usePaymentReadiness(): PaymentReadiness {
  const [readiness, setReadiness] = useState<PaymentReadiness>('checking')

  useEffect(() => {
    const controller = new AbortController()

    listCards(controller.signal).then(
      (cards) => setReadiness(cards.length > 0 ? 'ready' : 'missing'),
      () => {
        if (controller.signal.aborted) return
        setReadiness('ready')
      },
    )

    return () => controller.abort()
  }, [])

  return readiness
}
