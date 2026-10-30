import { useState } from 'react'
import { Link } from 'react-router'

import { useSession } from '@/features/auth/session'
import { CONNECTOR_TYPE_LABEL, formatPower } from '@/features/terminals/format'

import { formatMoney } from './charges'
import CancelBookingDialog from './components/CancelBookingDialog'
import { refreshMyBookings, useMyBookings } from './data/myBookingsStore'
import { formatRelative, formatWindow } from './slots'
import { bookingPhase, splitBookings, timeRemaining, type BookingPhase } from './timeline'
import type { Booking } from './types'
import { useNow } from './useNow'

/**
 * Las reservas del conductor: la activa arriba y el historial abajo (RF08).
 *
 * Es el contenido de la sección "Reservas e historial" del perfil. Vive en esta feature y el
 * perfil solo lo monta, igual que hace con Medios de pago: quien toque las reservas no tiene que
 * meterse en el perfil, y el perfil decide dónde se dibuja y nada más.
 *
 * **Separadas y no una sola lista**, que es lo que se pidió. Lo que el conductor viene a ver casi
 * siempre es la reserva que tiene por delante —cuándo es, dónde, cuánto falta— y cancelarla si no
 * llega. El historial es para consultar. En una sola lista ordenada por fecha, la reserva de
 * mañana quedaría entre las de la semana pasada.
 *
 * Qué cuenta como activa y qué como historial lo decide `timeline.ts`, no este archivo: la franja
 * de escritorio lee la misma regla y tienen que coincidir en el minuto en que una reserva termina.
 */
export default function MyBookings() {
  const session = useSession()
  const state = useMyBookings()

  /*
   * Cada medio minuto: alcanza para que "empieza en 40 min" no se quede atrás, y para que una
   * reserva pase sola de activa al historial al terminar sin tener que recargar.
   */
  const now = useNow(30_000)
  const [cancelling, setCancelling] = useState<Booking | null>(null)

  if (session === null) {
    return (
      <Notice>
        <Link to="/login" className="text-primary font-semibold underline underline-offset-4">
          Iniciá sesión
        </Link>{' '}
        para ver tus reservas.
      </Notice>
    )
  }

  if (session.role !== 'CONDUCTOR') {
    return <Notice>Las reservas son para cuentas de conductor.</Notice>
  }

  if (state.status === 'idle' || state.status === 'loading') {
    return <Notice>Cargando tus reservas…</Notice>
  }

  if (state.status === 'error') {
    return (
      <Notice tone="danger">
        {state.message}{' '}
        <button
          type="button"
          onClick={refreshMyBookings}
          className="text-text cursor-pointer font-semibold underline underline-offset-4"
        >
          Reintentar
        </button>
      </Notice>
    )
  }

  const { active, history } = splitBookings(state.bookings, now)
  const [next, ...laterOnes] = active

  return (
    <div className="flex flex-col gap-8 md:min-h-0 md:flex-1">
      <section aria-labelledby="active-bookings-title" className="flex flex-col gap-4">
        <SectionTitle id="active-bookings-title">
          {active.length > 1 ? 'Reservas activas' : 'Reserva activa'}
        </SectionTitle>

        {next === undefined ? (
          <EmptyActive />
        ) : (
          <>
            <ActiveBookingCard booking={next} now={now} onCancel={() => setCancelling(next)} />

            {/*
              Las que vienen después de la próxima van en renglones y no en otra tarjeta grande:
              compiten por la atención con la que importa ahora, y son las que menos urge mirar.
            */}
            {laterOnes.length > 0 && (
              <ul className="flex flex-col gap-2">
                {laterOnes.map((booking) => (
                  <li key={booking.id}>
                    <BookingRow booking={booking} onCancel={() => setCancelling(booking)} />
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </section>

      <section
        aria-labelledby="booking-history-title"
        className="flex flex-col gap-4 md:min-h-0 md:flex-1"
      >
        <SectionTitle id="booking-history-title">Historial</SectionTitle>

        {history.length === 0 ? (
          <p className="text-text-muted text-sm">
            Acá van a aparecer tus reservas terminadas y las que canceles.
          </p>
        ) : (
          /*
            En un recuadro de alto fijo con scroll propio, igual que los términos y condiciones: el
            historial solo crece, y sin tope empujaría la reserva activa fuera de la pantalla con
            reservas de hace meses. El borde marca dónde empieza y termina lo que se recorre.

            En PC y tablet el tope no es fijo: cuando la tarjeta llega al alto de la ventana, el
            recuadro se achica para entrar en lo que le queda. Así hay un solo scroll, el del
            historial, y no uno adentro de otro.
          */
          <ul className="border-border/60 flex max-h-80 flex-col gap-2 overflow-y-auto md:max-h-none md:min-h-40 md:flex-1 rounded-2xl border p-3">
            {history.map((booking) => (
              <li key={booking.id}>
                <BookingRow booking={booking} phase={bookingPhase(booking, now)} />
              </li>
            ))}
          </ul>
        )}
      </section>

      {cancelling !== null && (
        <CancelBookingDialog booking={cancelling} onClose={() => setCancelling(null)} />
      )}
    </div>
  )
}

/*
 * ---------------------------------------------------------------------------
 * Piezas
 * ---------------------------------------------------------------------------
 */

function SectionTitle({ id, children }: { id: string; children: React.ReactNode }) {
  return (
    <h2 id={id} className="text-text text-lg font-extrabold tracking-tight">
      {children}
    </h2>
  )
}

function Notice({
  children,
  tone = 'muted',
}: {
  children: React.ReactNode
  tone?: 'muted' | 'danger'
}) {
  return (
    <p
      className={`text-sm ${tone === 'danger' ? 'text-danger' : 'text-text-muted'}`}
      role={tone === 'danger' ? 'alert' : undefined}
    >
      {children}
    </p>
  )
}

/** El nombre del lugar, o lo único que se sabe si la estación ya no está en la red. */
function placeName(booking: Booking): string {
  return booking.location?.stationName ?? `Conector ${String(booking.connectorId)}`
}

/** "CCS2 · 150 kW · Av. 9 de Julio 1000", o el aviso de que la estación no está. */
function placeDetail(booking: Booking): string {
  const { location } = booking
  if (location === null) return 'La estación ya no está disponible en el mapa'
  return `${CONNECTOR_TYPE_LABEL[location.connectorType]} · ${formatPower(location.maxPowerKw)} · ${location.address}`
}

/** Sin reserva activa: lo que hay que hacer para tener una, con el camino a mano. */
function EmptyActive() {
  return (
    <div className="border-border bg-surface/40 flex flex-col items-start gap-3 rounded-2xl border border-dashed px-5 py-5 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-text-muted text-sm">
        No tenés ninguna reserva por delante. Buscá un conector en el mapa y bloqueá el horario que
        te sirva.
      </p>
      <Link
        to="/stations/map"
        className="brand-fill text-on-primary shrink-0 rounded-xl px-4 py-2.5 text-sm font-bold"
      >
        Ir al mapa
      </Link>
    </div>
  )
}

/**
 * Lo que se pagó por la reserva: la seña siempre, y la carga cuando se sepa cuánto costó.
 *
 * Los montos van juntos en un renglón propio y no mezclados con el lugar: es lo que se busca al
 * revisar un gasto, y así se encuentra sin leer el resto. La carga no aparece hasta tener un
 * número —ni en cero ni con un guion—: una reserva que todavía no se usó no costó "$ 0".
 */
function Charges({ booking }: { booking: Booking }) {
  return (
    <p className="text-text-muted flex flex-wrap gap-x-3 text-xs tabular-nums">
      <span>
        Seña <span className="text-text font-semibold">{formatMoney(booking.deposit)}</span>
      </span>
      {booking.chargingCost !== null && (
        <span>
          Carga <span className="text-text font-semibold">{formatMoney(booking.chargingCost)}</span>
        </span>
      )}
    </p>
  )
}

const CANCEL_BUTTON =
  'text-text-muted hover:text-danger focus-visible:outline-primary cursor-pointer rounded-xl px-3 py-2 text-sm font-semibold transition-colors focus-visible:outline-2'

/**
 * La reserva que viene: la tarjeta grande, con cuánto falta arriba de todo.
 *
 * "Cuánto falta" cambia de sentido con la fase, y el texto lo dice: antes de empezar es para que
 * empiece, y con la ventana corriendo es para que termine. Un solo número sin esa aclaración se
 * lee al revés la mitad del tiempo.
 */
function ActiveBookingCard({
  booking,
  now,
  onCancel,
}: {
  booking: Booking
  now: Date
  onCancel: () => void
}) {
  const phase = bookingPhase(booking, now)
  const remaining = timeRemaining(booking, now) ?? 0
  const inProgress = phase === 'inProgress'

  return (
    <article className="border-primary/40 bg-surface/50 flex flex-col gap-4 rounded-2xl border px-5 py-5">
      <p className="text-primary flex items-center gap-2 text-sm font-bold">
        <span aria-hidden="true" className="relative flex h-2.5 w-2.5 shrink-0">
          {inProgress && (
            <span className="bg-primary absolute inline-flex h-full w-full animate-ping rounded-full opacity-60 motion-reduce:animate-none" />
          )}
          <span className="bg-primary relative inline-flex h-2.5 w-2.5 rounded-full" />
        </span>
        {inProgress
          ? `En curso · termina en ${formatRelative(remaining)}`
          : `Empieza en ${formatRelative(remaining)}`}
      </p>

      <div className="flex flex-col gap-1">
        <h3 className="text-text text-xl font-extrabold tracking-tight">{placeName(booking)}</h3>
        <p className="text-text-muted text-sm">{placeDetail(booking)}</p>
      </div>

      <div className="border-border/60 flex flex-col gap-3 border-t pt-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-col gap-1">
          <p className="text-text text-sm font-semibold first-letter:uppercase">
            {formatWindow(booking.start, booking.end)}
          </p>
          <Charges booking={booking} />
        </div>
        <button
          type="button"
          onClick={onCancel}
          className={`${CANCEL_BUTTON} self-start sm:self-auto`}
        >
          Cancelar reserva
        </button>
      </div>
    </article>
  )
}

const PHASE_LABEL: Partial<Record<BookingPhase, string>> = {
  finished: 'Terminada',
  cancelled: 'Cancelada',
}

/**
 * El color de cada etiqueta, en el texto y en el borde: verde la que se cumplió, rojo la que se
 * canceló. Así el historial se lee de un vistazo sin leer las palabras.
 */
const PHASE_LABEL_CLASS: Partial<Record<BookingPhase, string>> = {
  finished: 'border-primary/60 text-primary',
  cancelled: 'border-danger/60 text-danger',
}

/**
 * Un renglón: el horario y el lugar, más la acción o el estado.
 *
 * Lo usan las dos listas. En las activas lleva el botón de cancelar; en el historial, la etiqueta
 * de cómo terminó. Nunca las dos: una reserva del historial ya no se puede cancelar.
 */
function BookingRow({
  booking,
  phase,
  onCancel,
}: {
  booking: Booking
  phase?: BookingPhase
  onCancel?: () => void
}) {
  const label = phase === undefined ? undefined : PHASE_LABEL[phase]
  const cancelled = phase === 'cancelled'

  return (
    <div className="border-border bg-surface/30 flex flex-col gap-2 rounded-2xl border px-4 py-3 sm:flex-row sm:items-center sm:gap-4">
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        {/* Una cancelada va tachada: se lee como algo que no pasó, que es lo que es. */}
        <p
          className={`text-sm font-semibold first-letter:uppercase ${
            cancelled ? 'text-text-muted line-through' : 'text-text'
          }`}
        >
          {formatWindow(booking.start, booking.end)}
        </p>
        <p className="text-text-muted truncate text-xs">
          {placeName(booking)}
          {booking.location !== null &&
            ` · ${CONNECTOR_TYPE_LABEL[booking.location.connectorType]} · ${formatPower(booking.location.maxPowerKw)}`}
        </p>
        <Charges booking={booking} />
      </div>

      {label !== undefined && (
        <span
          className={`self-start rounded-full border px-2.5 py-1 text-xs font-semibold sm:self-auto ${
            phase === undefined ? '' : (PHASE_LABEL_CLASS[phase] ?? '')
          }`}
        >
          {label}
        </span>
      )}

      {onCancel !== undefined && (
        <button
          type="button"
          onClick={onCancel}
          className={`${CANCEL_BUTTON} self-start sm:self-auto`}
        >
          Cancelar
        </button>
      )}
    </div>
  )
}
