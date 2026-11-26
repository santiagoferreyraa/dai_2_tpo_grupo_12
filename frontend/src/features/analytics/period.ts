/**
 * El período del tablero: los tres atajos y el rango a mano.
 *
 * El backend no acepta más de 31 días (`INVALID_DATE_RANGE`), así que el rango a mano se valida
 * acá antes de pedir nada: el error se dice al lado de las fechas y no después de un viaje.
 */

export type PeriodKey = '24h' | '7d' | '30d' | 'custom'

export interface Period {
  key: PeriodKey
  from: Date
  to: Date
}

export const PRESETS: { key: Exclude<PeriodKey, 'custom'>; label: string }[] = [
  { key: '24h', label: '24 h' },
  { key: '7d', label: '7 días' },
  { key: '30d', label: '30 días' },
]

export const MAX_RANGE_DAYS = 31

const HOUR_MS = 3_600_000
const DAY_MS = 24 * HOUR_MS

export function presetPeriod(key: Exclude<PeriodKey, 'custom'>, now = new Date()): Period {
  const span = key === '24h' ? DAY_MS : key === '7d' ? 7 * DAY_MS : 30 * DAY_MS
  return { key, from: new Date(now.getTime() - span), to: now }
}

/** `yyyy-mm-dd` de un `<input type="date">`, en hora local. */
export function toDateInput(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/**
 * El rango a mano, de la medianoche del primer día a la del día siguiente al último: elegir "del 3
 * al 5" incluye el 5 entero. Devuelve el motivo si no sirve.
 */
export function customPeriod(fromText: string, toText: string): Period | string {
  if (fromText === '' || toText === '') return 'Elegí las dos fechas.'
  const from = new Date(`${fromText}T00:00:00`)
  const to = new Date(`${toText}T00:00:00`)
  to.setDate(to.getDate() + 1)
  if (Number.isNaN(from.getTime()) || Number.isNaN(to.getTime()))
    return 'Alguna fecha no es válida.'
  if (from >= to) return 'La fecha de inicio tiene que ser anterior a la de fin.'
  if (to.getTime() - from.getTime() > MAX_RANGE_DAYS * DAY_MS) {
    return `El rango no puede pasar de ${MAX_RANGE_DAYS} días.`
  }
  return { key: 'custom', from, to }
}
