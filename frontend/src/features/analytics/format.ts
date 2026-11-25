/** Formato de los números y las horas del tablero, siempre en es-AR y en hora local. */

const ONE_DECIMAL = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 1 })
const INTEGER = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 })

/** `1.234,5`: un decimal como mucho. Lo que pasa de mil va sin decimales, que ahí no dicen nada. */
export function formatAmount(value: number): string {
  return Math.abs(value) >= 1000 ? INTEGER.format(value) : ONE_DECIMAL.format(value)
}

export function formatCount(value: number): string {
  return INTEGER.format(value)
}

/* Reloj de 24 h: con "p. m." las marcas del eje no entran y el pico se lee peor. */
const TIME = new Intl.DateTimeFormat('es-AR', {
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})
const DAY = new Intl.DateTimeFormat('es-AR', { weekday: 'short', day: 'numeric' })
const DATE = new Intl.DateTimeFormat('es-AR', { day: '2-digit', month: '2-digit' })

export function formatTime(date: Date): string {
  return TIME.format(date)
}

export function formatDay(date: Date): string {
  return DAY.format(date)
}

export function formatShortDate(date: Date): string {
  return DATE.format(date)
}

/** "hoy 19:15", "ayer 08:30" o "lun 23 19:15": cuándo fue el pico. */
export function formatMoment(date: Date, now = new Date()): string {
  const startOfToday = new Date(now)
  startOfToday.setHours(0, 0, 0, 0)
  const diffDays = Math.floor((startOfToday.getTime() - date.getTime()) / 86_400_000) + 1
  if (date >= startOfToday) return `hoy ${formatTime(date)}`
  if (diffDays === 1) return `ayer ${formatTime(date)}`
  return `${formatDay(date)} ${formatTime(date)}`
}

/** Para comparar nombres sin que importen mayúsculas ni tildes. */
export function normalize(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim()
}
