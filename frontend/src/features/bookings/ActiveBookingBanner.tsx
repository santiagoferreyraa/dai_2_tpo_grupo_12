import { Link, useLocation } from 'react-router'

import { CONNECTOR_TYPE_LABEL, formatPower } from '@/features/terminals/format'

import BookingTicket from './components/BookingTicket'
import { useMyBookings } from './data/myBookingsStore'
import { formatCountdown, formatRelative, formatTime, formatWindow, isSameDay } from './slots'
import { bookingPhase, nextBooking, timeRemaining } from './timeline'
import { useNow } from './useNow'

/**
 * La reserva activa, a la vista en cualquier pantalla: una franja debajo de la navegación en PC y
 * tablet, y una tarjeta flotante encima de la barra en el celular.
 *
 * **Es cómo el conductor se entera de que tiene una reserva**, en cualquier pantalla y sin ir a
 * buscarla: la misma idea que el viaje en curso de una app de autos. Dice lo que se necesita de un
 * vistazo —cuánto falta, dónde, con qué conector— y lleva a la reserva o a cómo llegar.
 *
 * **Empuja el contenido en vez de flotar encima.** Una franja superpuesta taparía justo lo de
 * arriba de cada pantalla —el buscador del mapa, la cabecera del perfil—, y no hay forma de saber
 * qué hay debajo en cada una. Va en el flujo del `<main>`, así que cada pantalla simplemente
 * empieza más abajo.
 *
 * **En el celular es otra forma con los mismos datos**: `BookingTicket`, que flota encima de la
 * barra de abajo. Las dos salen de acá para que decidan con la misma cuenta qué reserva mostrar y
 * cuánto falta; cada una se esconde en el ancho que no le toca.
 *
 * Qué reserva muestra lo decide `nextBooking`, la misma regla que usa el perfil: la activa más
 * próxima, y una que está corriendo le gana a una que todavía no empezó. Cuando la ventana termina
 * la franja desaparece sola, sin recargar.
 */
export default function ActiveBookingBanner() {
  const { pathname } = useLocation()
  const state = useMyBookings()

  /*
   * Cada segundo, porque cuando falta menos de una hora la franja muestra la cuenta regresiva con
   * segundos. Es un solo componente y un solo reloj, así que el costo es nada.
   */
  const now = useNow(1000)

  if (state.status !== 'ready') return null

  const booking = nextBooking(state.bookings, now)
  if (booking === null) return null

  /*
   * En el perfil no aparece. Ahí arriba ya están la cabecera y la tarjeta de la sección, y el aviso
   * de que hay una reserva lo da el punto que titila sobre el ícono de "Reservas e historial". Ver
   * `ActiveBookingDot`.
   */
  if (pathname === '/profile' || pathname.startsWith('/profile/')) return null

  const phase = bookingPhase(booking, now)
  const remaining = timeRemaining(booking, now) ?? 0
  const inProgress = phase === 'inProgress'

  /*
   * Con la ventana corriendo, cuánto de ella ya pasó. Es la barra del borde de abajo: dice de un
   * vistazo si recién empieza o si está por terminar, sin leer ningún número.
   */
  const total = booking.end.getTime() - booking.start.getTime()
  const elapsed = inProgress ? Math.min(1, (now.getTime() - booking.start.getTime()) / total) : 0

  /* A menos de una hora se cuenta con segundos; más lejos, en palabras. */
  const countdown = remaining < 3_600_000 ? formatCountdown(remaining) : formatRelative(remaining)

  /*
   * El progreso de la tarjeta del celular: hacia el próximo hito. En curso, cuánto de la ventana ya
   * pasó. Antes de empezar, cuánto de la última hora ya pasó: los segmentos se llenan a medida que
   * la reserva se acerca, y a más de una hora quedan vacíos, porque todavía no hay nada que medir.
   */
  const progress = inProgress ? elapsed : Math.max(0, 1 - remaining / 3_600_000)

  const { location } = booking
  const directionsUrl =
    location === null
      ? null
      : `https://www.google.com/maps/dir/?api=1&destination=${String(location.latitude)},${String(location.longitude)}`

  return (
    <>
      <BookingTicket
        /* Otra reserva es otra tarjeta: no hereda si la anterior estaba achicada o a mitad de animación. */
        key={booking.id}
        booking={booking}
        countdown={countdown}
        countdownLabel={inProgress ? 'termina en' : 'empieza en'}
        progress={progress}
        remainingMs={remaining}
      />
      {/*
      El `px-6` es el de la franja de navegación de arriba, así los bordes de las dos coinciden y se
      leen como una sola pieza. El `pb-2` separa la franja del contenido sin comerle alto al mapa.

      El `z-[1145]` la pone por ENCIMA del velo de la navegación, que baja un poco más abajo del
      borde de la franja para desvanecer ahí el contenido que se corta. Esta tira empieza justo en
      ese borde, así que sin el z-index le tocaría la mitad del velo y saldría borroneada. Puede
      ganarle a la franja —que está en 1140— sin tapar nada, porque no se superponen: la franja
      ocupa los primeros 5rem y esta tira empieza donde la franja termina. Y puede hacerlo sin
      riesgo porque no scrollea: las pantallas scrollean adentro de su propia caja, así que la
      tira nunca se mueve de ahí. Ver `.top-bar__fade`.
    */}
      <div className="relative z-[1145] hidden shrink-0 px-6 pb-2 md:block">
        <section
          aria-label="Tu reserva activa"
          /*
          En curso, un aro verde. Es `ring` y no `border` porque el borde lo fija `.glass-panel`, que
          le gana a las utilidades de Tailwind.
        */
          className={`glass-panel relative flex items-center gap-5 overflow-hidden rounded-2xl px-5 py-3 ${
            inProgress ? 'ring-primary/50 ring-1' : ''
          }`}
        >
          {/* El estado, con el punto que late mientras la ventana corre. */}
          <div className="flex shrink-0 items-center gap-3">
            <span aria-hidden="true" className="relative flex h-3 w-3">
              {inProgress && (
                <span className="bg-primary absolute inline-flex h-full w-full animate-ping rounded-full opacity-60 motion-reduce:animate-none" />
              )}
              <span className="bg-primary relative inline-flex h-3 w-3 rounded-full" />
            </span>

            <div className="flex flex-col">
              <span className="text-text-muted text-xs font-semibold tracking-wide uppercase">
                {inProgress ? 'Reserva en curso · termina en' : 'Tu reserva empieza en'}
              </span>
              {/*
              Sin `aria-live`: con la cuenta regresiva de a segundo, un lector de pantalla no
              pararía de anunciar. El número está escrito y se lee cuando se lo busca.
            */}
              <span className="text-text text-lg leading-tight font-extrabold tabular-nums">
                {countdown}
              </span>
            </div>
          </div>

          <span aria-hidden="true" className="bg-border/70 h-9 w-px shrink-0" />

          {/*
          El lugar y el horario. `min-w-0` para que un nombre o una dirección larga se corten con
          puntos suspensivos en vez de empujar los botones fuera de la franja.
        */}
          <div className="flex min-w-0 flex-1 flex-col">
            <span className="text-text truncate text-sm font-bold">
              {location?.stationName ?? `Conector ${String(booking.connectorId)}`}
              {location !== null && (
                <span className="text-text-muted font-medium">
                  {' '}
                  · {CONNECTOR_TYPE_LABEL[location.connectorType]} ·{' '}
                  {formatPower(location.maxPowerKw)}
                </span>
              )}
            </span>
            <span className="text-text-muted truncate text-xs">
              {/*
              Hoy alcanza con la hora. Para otro día hace falta la fecha entera, o "empieza en 2
              días" deja adivinando cuál.
            */}
              {isSameDay(booking.start, now)
                ? `Hoy, de ${formatTime(booking.start)} a ${formatTime(booking.end)}`
                : capitalize(formatWindow(booking.start, booking.end))}
              {location !== null && ` · ${location.address}`}
            </span>
          </div>

          <div className="flex shrink-0 items-center gap-2">
            {directionsUrl !== null && (
              /*
              Afuera de la app y en otra pestaña: es el mapa del teléfono o de Google el que sabe
              trazar el camino, y la reserva no tiene por qué perderse de vista para eso.
            */
              <a
                href={directionsUrl}
                target="_blank"
                rel="noreferrer"
                className="text-text-muted hover:text-text focus-visible:outline-primary rounded-xl px-3 py-2 text-sm font-semibold transition-colors focus-visible:outline-2"
              >
                Cómo llegar
              </a>
            )}
            <Link
              to="/profile/reservations"
              className="brand-fill text-on-primary focus-visible:outline-primary rounded-xl px-4 py-2 text-sm font-bold focus-visible:outline-2 focus-visible:outline-offset-2"
            >
              Ver reserva
            </Link>
          </div>

          {inProgress && (
            <span aria-hidden="true" className="bg-primary/20 absolute inset-x-0 bottom-0 h-0.5">
              <span
                className="bg-primary block h-full transition-[width] duration-1000 ease-linear"
                style={{ width: `${String(elapsed * 100)}%` }}
              />
            </span>
          )}
        </section>
      </div>
    </>
  )
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}
