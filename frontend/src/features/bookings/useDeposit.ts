import { useEffect, useState } from 'react'

import { fetchDeposit } from './data/depositRepository'

/**
 * La seña del conector, para anunciarla antes de que el conductor se comprometa (ECO-53).
 *
 * - `loading`: todavía no llegó.
 * - `ready`: el monto; 0 si el conector no pide seña.
 * - `unknown`: Tarificación no contestó. Se puede seguir: el checkout la vuelve a pedir, y si
 *   tampoco la consigue, no confirma y lo dice.
 */
export type DepositState =
  { status: 'loading' } | { status: 'ready'; amount: number } | { status: 'unknown' }

export function useDeposit(connectorId: number): DepositState {
  /*
   * La respuesta se guarda junto con el conector al que corresponde. Si cambia el conector, lo
   * guardado es de otro y se lee como "cargando" sin tocar el estado dentro del efecto.
   */
  const [answer, setAnswer] = useState<{ connectorId: number; state: DepositState } | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    fetchDeposit(connectorId, controller.signal).then(
      (amount) => {
        if (controller.signal.aborted) return
        setAnswer({
          connectorId,
          state: amount === null ? { status: 'unknown' } : { status: 'ready', amount },
        })
      },
      () => {
        if (!controller.signal.aborted) setAnswer({ connectorId, state: { status: 'unknown' } })
      },
    )
    return () => controller.abort()
  }, [connectorId])

  return answer?.connectorId === connectorId ? answer.state : { status: 'loading' }
}
