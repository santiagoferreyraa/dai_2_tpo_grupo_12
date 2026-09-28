/**
 * Los horarios que se ofrecen para reservar, y cómo se escriben en pantalla.
 *
 * Todo es hora LOCAL del navegador: el conductor piensa "el miércoles a las seis de la tarde", no
 * en UTC. La conversión a UTC la hace el repositorio al mandar, con `toISOString`.
 *
 * **Lo que se puede elegir ya respeta los límites del backend**, así que una ventana armada con
 * estas opciones nunca rebota con un 400. Los límites son configuración de
 * `ecopedia-charging` (`ecopedia.booking.*`) y están repetidos acá a propósito, porque no hay
 * endpoint que los publique. Si cambian allá, cambian acá:
 *
 * - `max-window`: PT4H. Por eso la duración más larga es de cuatro horas.
 * - `max-horizon`: P30D. Por eso los días son hoy y los veintinueve siguientes.
 *
 * Igual que `timeline.ts`, todo recibe `now` en vez de mirar la hora del sistema.
 */

/** Cuántos días se ofrecen, contando hoy. Uno menos que el horizonte, para no rozar el borde. */
export const BOOKABLE_DAYS = 30

/** Cada cuánto arranca un turno. Un cuarto de hora es lo que alcanza sin llenar la lista. */
export const SLOT_STEP_MINUTES = 15

/** Las duraciones que se ofrecen, en minutos. La última es el tope del backend. */
export const DURATIONS_MINUTES = [30, 60, 120, 240] as const

/**
 * El margen mínimo entre ahora y el inicio de la ventana.
 *
 * Sin margen, el turno de las 18:00 se ofrecería a las 17:59:58 y llegaría al backend ya
 * empezado. Cinco minutos cubren lo que tarda en decidirse quien lo mira y el viaje del pedido.
 */
const LEAD_MINUTES = 5

const MINUTE_MS = 60_000

/** El comienzo del día de `date`, en hora local. */
export function startOfDay(date: Date): Date {
  const day = new Date(date)
  day.setHours(0, 0, 0, 0)
  return day
}

/** `date` corrido `days` días, respetando la hora local. */
function addDays(date: Date, days: number): Date {
  const shifted = new Date(date)
  shifted.setDate(shifted.getDate() + days)
  return shifted
}

/** Si dos fechas caen el mismo día, en hora local. */
export function isSameDay(a: Date, b: Date): boolean {
  return startOfDay(a).getTime() === startOfDay(b).getTime()
}

/**
 * Los horarios de inicio que se pueden elegir en un día, de a cuarto de hora.
 *
 * En los días que vienen son los noventa y seis del día entero. Hoy son solo los que todavía
 * dejan el margen: a las 17:52 el primero es el de las 18:00, y a las 17:57 el de las 18:15.
 */
export function startTimesFor(day: Date, now: Date): Date[] {
  const earliest = now.getTime() + LEAD_MINUTES * MINUTE_MS
  const times: Date[] = []

  for (let minutes = 0; minutes < 24 * 60; minutes += SLOT_STEP_MINUTES) {
    const time = startOfDay(day)
    time.setMinutes(minutes)
    if (time.getTime() >= earliest) times.push(time)
  }

  return times
}

/**
 * Los días que se ofrecen: hoy y los siguientes, salvo hoy si ya no le queda ningún turno.
 *
 * A las 23:57 no queda ningún inicio con el margen, y un día que se puede tocar pero no tiene
 * horarios es un callejón. Sale de la lista en vez de quedar apagado.
 */
export function bookableDays(now: Date): Date[] {
  const today = startOfDay(now)
  const days: Date[] = []

  for (let offset = 0; offset < BOOKABLE_DAYS; offset++) {
    const day = addDays(today, offset)
    if (offset === 0 && startTimesFor(day, now).length === 0) continue
    days.push(day)
  }

  return days
}

/** El fin de una ventana que empieza en `start` y dura `minutes`. */
export function windowEnd(start: Date, minutes: number): Date {
  return new Date(start.getTime() + minutes * MINUTE_MS)
}

/**
 * La tolerancia para presentarse, en minutos desde el inicio de la reserva.
 *
 * Es el margen de gracia de RF09: pasado ese plazo sin que el conductor se presente, la reserva
 * se cancela. **Todavía no lo aplica el backend** —la cancelación automática no está
 * implementada—, así que por ahora es lo que el conductor acepta al reservar. Cuando exista,
 * será configuración de `ecopedia-charging` y este número tiene que coincidir, igual que los
 * límites de arriba.
 */
export const GRACE_MINUTES = 15

/** Hasta cuándo puede presentarse el conductor a una reserva que empieza en `start`. */
export function graceDeadline(start: Date): Date {
  return new Date(start.getTime() + GRACE_MINUTES * MINUTE_MS)
}

/*
 * ---------------------------------------------------------------------------
 * Cómo se escribe
 * ---------------------------------------------------------------------------
 */

const WEEKDAY = new Intl.DateTimeFormat('es-AR', { weekday: 'short' })
const DAY_NUMBER = new Intl.DateTimeFormat('es-AR', { day: 'numeric' })
const LONG_WEEKDAY = new Intl.DateTimeFormat('es-AR', { weekday: 'long' })
const MONTH = new Intl.DateTimeFormat('es-AR', { month: 'long' })
const TIME = new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false })

/** Sin el punto que `Intl` le pone a las abreviaturas en castellano: "mié", no "mié.". */
function withoutDot(text: string): string {
  return text.replace('.', '')
}

/**
 * El nombre corto de un día para su botón: "Hoy", "Mañana" o el día de la semana.
 *
 * Va separado del número (`dayNumber`) porque el botón los muestra en dos renglones.
 */
export function dayName(day: Date, now: Date): string {
  if (isSameDay(day, now)) return 'Hoy'
  if (isSameDay(day, addDays(now, 1))) return 'Mañana'
  const name = withoutDot(WEEKDAY.format(day))
  return name.charAt(0).toUpperCase() + name.slice(1)
}

export function dayNumber(day: Date): string {
  return DAY_NUMBER.format(day)
}

/** Una hora como "18:00". */
export function formatTime(date: Date): string {
  return TIME.format(date)
}

/**
 * Una hora que puede caer al día siguiente de `reference`: "18:15", o "00:05 del día siguiente".
 *
 * Por lo mismo que `formatWindow`: una reserva a las 23:50 tiene tolerancia hasta las 00:05, y
 * "hasta las 00:05" a secas se lee como una hora que ya pasó.
 */
export function formatTimeAfter(reference: Date, date: Date): string {
  return isSameDay(reference, date) ? formatTime(date) : `${formatTime(date)} del día siguiente`
}

/** Una duración como "30 min", "1 h" o "4 h". */
export function formatDuration(minutes: number): string {
  if (minutes < 60) return `${String(minutes)} min`
  const hours = minutes / 60
  return `${hours.toLocaleString('es-AR', { maximumFractionDigits: 1 })} h`
}

/**
 * Un día escrito entero: "miércoles 16 de septiembre".
 *
 * Se arma por partes y no con un solo formato de `Intl` porque ese pone una coma después del día
 * de la semana, y seguida de la hora la frase queda con dos comas.
 */
export function formatLongDate(date: Date): string {
  return `${LONG_WEEKDAY.format(date)} ${DAY_NUMBER.format(date)} de ${MONTH.format(date)}`
}

/**
 * Una ventana entera, para leer de corrido: "miércoles 16 de septiembre, de 18:00 a 19:00".
 *
 * Si termina al día siguiente lo dice, porque "de 23:00 a 01:00" sin más se lee como un error.
 */
export function formatWindow(start: Date, end: Date): string {
  const endsNextDay = !isSameDay(start, end)
  const endText = endsNextDay ? `${formatTime(end)} del día siguiente` : formatTime(end)
  return `${formatLongDate(start)}, de ${formatTime(start)} a ${endText}`
}

/**
 * Una cuenta regresiva como "9:42", o "1:05:30" si pasa de la hora.
 *
 * Redondea para arriba: a los 0,4 segundos todavía queda algo, y mostrar "0:00" con el plazo
 * sin vencer invita a pensar que ya se perdió.
 */
export function formatCountdown(ms: number): string {
  const totalSeconds = Math.max(0, Math.ceil(ms / 1000))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = String(totalSeconds % 60).padStart(2, '0')
  if (hours > 0) return `${String(hours)}:${String(minutes).padStart(2, '0')}:${seconds}`
  return `${String(minutes)}:${seconds}`
}

/**
 * Cuánto falta, dicho como lo diría alguien: "menos de un minuto", "40 min", "2 h 15 min",
 * "3 días".
 *
 * Es para leer de pasada —"empieza en 2 h 15 min"—, no para contar: la cuenta regresiva exacta,
 * con segundos, es `formatCountdown`. Por eso a partir del día se redondea a días: a esa distancia
 * los minutos no le cambian nada a nadie.
 */
export function formatRelative(ms: number): string {
  const minutes = Math.ceil(Math.max(0, ms) / MINUTE_MS)
  if (minutes < 1) return 'menos de un minuto'
  if (minutes < 60) return `${String(minutes)} min`

  const hours = Math.floor(minutes / 60)
  if (hours < 24) {
    const rest = minutes % 60
    return rest === 0 ? `${String(hours)} h` : `${String(hours)} h ${String(rest)} min`
  }

  const days = Math.round(hours / 24)
  return days === 1 ? '1 día' : `${String(days)} días`
}

/**
 * La cuenta regresiva en lo que entra en una burbuja: "32:23" a menos de una hora, "2 h" o "3 d"
 * más lejos. Redondea para arriba, igual que `formatCountdown`: con algo por delante no dice cero.
 */
export function formatCompactCountdown(ms: number): string {
  if (ms < 3_600_000) return formatCountdown(ms)
  const hours = Math.ceil(ms / 3_600_000)
  if (hours < 24) return `${String(hours)} h`
  return `${String(Math.ceil(hours / 24))} d`
}
