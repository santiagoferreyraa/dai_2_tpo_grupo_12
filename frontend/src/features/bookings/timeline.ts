import type { Booking } from './types'

/**
 * Dónde está parada cada reserva en el tiempo: la regla que separa la activa del historial.
 *
 * Vive aparte del repositorio porque no es un dato del backend sino una lectura de él contra el
 * reloj, y la hacen tres lugares que tienen que decir lo mismo: la franja de escritorio, la
 * sección "Reservas e historial" del perfil y la tarjeta de próxima carga de la portada. Si cada
 * uno decidiera por su cuenta cuándo una reserva deja de estar activa, en el minuto en que termina
 * la franja podría seguir mostrándola mientras el perfil ya la pasó al historial.
 *
 * Todas las funciones reciben `now` en vez de preguntarle la hora al sistema. Así quien dibuja
 * decide cada cuánto se recalcula —la franja, cada segundo; la lista, al cargar—, y la regla se
 * puede probar con una hora fija.
 */

/**
 * En qué momento de su vida está una reserva.
 *
 * - `upcoming`: confirmada y todavía no empezó. Lo que falta es para que empiece.
 * - `inProgress`: confirmada y su ventana está corriendo. Lo que falta es para que termine.
 * - `finished`: confirmada, pero su ventana ya pasó.
 * - `cancelled`: la canceló el conductor.
 * - `noShow`: el conductor no se presentó; pasó la tolerancia y el backend la cerró (ECO-57).
 *
 * `inProgress` no significa que el auto esté cargando: eso es una sesión de carga (RF11), otro
 * componente que todavía no existe. Significa que el conector está bloqueado para él ahora mismo.
 */
export type BookingPhase = 'upcoming' | 'inProgress' | 'finished' | 'cancelled' | 'noShow'

/**
 * La fase de una reserva en un instante.
 *
 * La ventana es semiabierta, `[start, end)`, igual que en el backend: en el instante exacto del
 * fin la reserva ya terminó, y en el del inicio ya está corriendo.
 */
export function bookingPhase(booking: Booking, now: Date): BookingPhase {
  if (booking.status === 'CANCELLED') return 'cancelled'
  if (booking.status === 'NO_SHOW') return 'noShow'
  if (now.getTime() >= booking.end.getTime()) return 'finished'
  if (now.getTime() >= booking.start.getTime()) return 'inProgress'
  return 'upcoming'
}

/** Si la reserva todavía le reserva algo al conductor: confirmada y sin terminar. */
export function isActive(booking: Booking, now: Date): boolean {
  const phase = bookingPhase(booking, now)
  return phase === 'upcoming' || phase === 'inProgress'
}

export interface BookingTimeline {
  /** Las que siguen valiendo, de la más próxima a la más lejana. La primera es la de la franja. */
  active: Booking[]
  /** Las terminadas y las canceladas, de la más reciente a la más vieja. */
  history: Booking[]
}

/**
 * Parte las reservas en activas e historial.
 *
 * **Los dos órdenes son opuestos a propósito.** En las activas lo que importa es lo que viene
 * primero; en el historial, lo último que pasó. Una lista de historial que empieza por la reserva
 * de hace tres meses obliga a bajar hasta el final para ver la de ayer.
 *
 * Ordena acá aunque el backend ya las mande por inicio, porque el historial va al revés y porque
 * la regla no tiene que depender de un orden que decide otro proceso.
 */
export function splitBookings(bookings: readonly Booking[], now: Date): BookingTimeline {
  const active: Booking[] = []
  const history: Booking[] = []

  for (const booking of bookings) {
    if (isActive(booking, now)) active.push(booking)
    else history.push(booking)
  }

  active.sort((a, b) => a.start.getTime() - b.start.getTime())
  history.sort((a, b) => b.start.getTime() - a.start.getTime())

  return { active, history }
}

/**
 * La reserva que va en la franja: la activa más próxima, o `null` si no hay ninguna.
 *
 * Una que está corriendo siempre gana a una que todavía no empezó, y sale sola del orden por
 * inicio: la que está corriendo empezó antes.
 */
export function nextBooking(bookings: readonly Booking[], now: Date): Booking | null {
  return splitBookings(bookings, now).active[0] ?? null
}

/**
 * Cuánto falta para el próximo hito de la reserva, en milisegundos: el inicio si todavía no
 * empezó, el fin si está corriendo. `null` para las que ya no tienen nada por delante.
 *
 * Nunca devuelve un número negativo. Entre dos recálculos la hora puede pasar el hito, y la
 * franja mostraría "-00:01" durante un segundo.
 */
export function timeRemaining(booking: Booking, now: Date): number | null {
  const phase = bookingPhase(booking, now)
  if (phase === 'upcoming') return Math.max(0, booking.start.getTime() - now.getTime())
  if (phase === 'inProgress') return Math.max(0, booking.end.getTime() - now.getTime())
  return null
}
