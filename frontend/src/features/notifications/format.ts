import { BoltIcon, CalendarIcon, CrossIcon } from '@/features/navigation/icons'

import type { NotificationType } from './types'

/**
 * Cómo se dibuja y cómo se cuenta un aviso: lo que la pantalla necesita y el backend no manda.
 *
 * El texto viene armado de `NotificationTemplates` y no se toca (ver `types.ts`). Lo que falta es
 * de presentación: con qué ícono se muestra cada tipo y cómo se dice cuándo llegó.
 */

/** El dibujo y el color de cada tipo de aviso. */
interface NotificationLook {
  Icon: (props: { className?: string }) => React.ReactElement
  /**
   * La tinta del ícono.
   *
   * Solo dos valores posibles y no un color libre: el aviso de una reserva confirmada es una buena
   * noticia y va en el verde de la marca; el de una cancelación no es un error de la aplicación
   * —nadie hizo nada mal— pero sí algo que se perdió, y el rojo es lo que la aplicación usa para
   * eso. Un tercer color sería inventar una categoría que no existe.
   */
  tint: 'text-primary' | 'text-danger'
}

/**
 * Qué dibujo le toca a cada tipo.
 *
 * Es un `Record` completo y no un `switch` con caso por omisión a propósito: el día que el backend
 * agregue un tipo, `NotificationType` crece y TypeScript marca este objeto como incompleto. Con un
 * caso por omisión, el aviso nuevo se dibujaría con el ícono de otra cosa y nadie se enteraría.
 */
const NOTIFICATION_LOOK: Record<NotificationType, NotificationLook> = {
  BOOKING_CONFIRMED: { Icon: CalendarIcon, tint: 'text-primary' },
  BOOKING_CANCELLED: { Icon: CrossIcon, tint: 'text-danger' },
}

/**
 * El dibujo de un aviso, o el genérico si llega un tipo que esta versión no conoce.
 *
 * El respaldo existe porque los dos lados se despliegan por separado: un backend más nuevo puede
 * mandar un tipo que este frontend no tiene en su unión, y eso no puede terminar en una pantalla
 * rota. Se muestra con el rayo de la marca —el aviso igual trae su texto, que es lo que importa—.
 */
export function notificationLook(type: NotificationType): NotificationLook {
  return NOTIFICATION_LOOK[type] ?? { Icon: BoltIcon, tint: 'text-primary' }
}

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/**
 * Cuándo llegó, dicho como lo diría alguien: "recién", "hace 5 min", "ayer", "12 sept".
 *
 * **Por qué no la hora exacta.** Un aviso no se lee para saber a qué hora llegó sino para saber si
 * es de ahora o de la semana pasada, y "hace 5 min" contesta eso de un vistazo mientras que
 * "14:32" obliga a mirar el reloj y restar. La fecha completa vuelve recién cuando la distancia ya
 * no se puede sentir —de una semana para atrás—, que es cuando el día concreto empieza a importar
 * más que la distancia.
 *
 * El futuro se trata como "recién" y no con un "en 5 min": un aviso no puede llegar de mañana, así
 * que un instante futuro solo puede ser el reloj de la máquina corrido unos segundos respecto del
 * servidor, y decirlo en pantalla sería mostrar un error que no le sirve a nadie.
 *
 * @param now se pasa desde afuera para que el componente decida cada cuánto se recalcula, y para
 *   que la función se pueda probar sin tocar el reloj.
 */
export function timeAgo(date: Date, now: Date): string {
  const elapsed = now.getTime() - date.getTime()

  if (elapsed < MINUTE) return 'Recién'
  if (elapsed < HOUR) return `Hace ${String(Math.floor(elapsed / MINUTE))} min`
  if (elapsed < DAY) return `Hace ${String(Math.floor(elapsed / HOUR))} h`

  /*
   * "Ayer" y no "hace 1 día": son la misma distancia pero no la misma frase, y la que se usa
   * hablando es la primera. Después de eso los días se cuentan hasta la semana.
   */
  const days = Math.floor(elapsed / DAY)
  if (days === 1) return 'Ayer'
  if (days < 7) return `Hace ${String(days)} días`

  return date.toLocaleDateString('es-AR', { day: 'numeric', month: 'short' })
}
