import type { Debt } from './types'

/**
 * Cómo se nombra y se muestra una deuda (ECO-62).
 *
 * El monto lleva centavos si los tiene, a diferencia de la seña (`formatMoney` en bookings, que
 * los corta): la seña la fija el operador en pesos redondos, pero el consumo sale de kWh por
 * precio y puede no ser redondo. Mostrar $ 8.400 y cobrar $ 8.400,50 sería decir un número y
 * cobrar otro.
 */
const MONEY = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
})

export function formatDebtAmount(amount: number): string {
  return MONEY.format(amount)
}

/** "Carga del 18/11": la deuda se reconoce por la carga que la generó. */
export function describeDebt(debt: Debt): string {
  const day = new Date(debt.incurredAt).toLocaleDateString('es-AR', {
    day: '2-digit',
    month: '2-digit',
  })
  return `Carga del ${day}`
}
