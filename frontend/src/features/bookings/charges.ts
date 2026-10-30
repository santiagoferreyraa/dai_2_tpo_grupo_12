/**
 * Lo que se le cobra al conductor por una reserva: la seña al confirmar y, más adelante, la carga.
 *
 * **La seña es fija y está escrita acá** hasta que el backend la devuelva con cada reserva. El
 * checkout ya calcula una por conector (`depositAmount`, RF06), pero `/bookings/mine` no la manda,
 * y el historial tiene que mostrar un monto. Mientras tanto se usa este para todas, en la revisión
 * y en el historial por igual: que una pantalla diga un número y la otra otro sería peor.
 */
export const DEPOSIT_ARS = 12_000

const MONEY = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  maximumFractionDigits: 0,
})

/** "$ 12.000". Sin centavos: ninguno de los montos que se muestran los tiene. */
export function formatMoney(amount: number): string {
  return MONEY.format(amount)
}
