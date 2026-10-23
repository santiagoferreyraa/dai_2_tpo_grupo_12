import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'

import DepartureField from '@/features/terminals/components/DepartureField'
import { CONNECTOR_TYPE_LABEL, formatPower } from '@/features/terminals/format'
import type { ConnectorSummary, StationResult } from '@/features/terminals/types'
import { useWheelToHorizontal } from '@/features/terminals/useWheelToHorizontal'

import {
  bookableDays,
  dayName,
  dayNumber,
  DURATIONS_MINUTES,
  formatCountdown,
  formatDuration,
  formatTime,
  formatTimeAfter,
  formatWindow,
  GRACE_MINUTES,
  graceDeadline,
  isSameDay,
  startTimesFor,
  windowEnd,
} from '../slots'
import { dayHasRoom, fits, startIsAvailable, useAvailability } from '../availability'
import type { Booking, Hold } from '../types'
import { useDragToScroll } from '../useDragToScroll'
import { pendingHoldFor, type BookingFlow, type BookingFlowError } from '../useBookingFlow'
import { useNow } from '../useNow'
import { usePaymentReadiness } from '../usePaymentReadiness'

/**
 * El contenido de reservar un conector: elegir el horario, revisar y confirmar.
 *
 * **Es solo el contenido, y el mismo en las dos formas.** En pantalla ancha lo envuelve
 * `BookingDialog`, centrado sobre el fondo oscurecido; en el celular va adentro del panel que sube
 * desde abajo, en lugar del detalle de la estación. Escribirlo una vez es lo que evita que las dos
 * formas digan cosas distintas cuando cambie un texto o una regla.
 *
 * El paso en que se está lo lleva `flow`, que le pasa quien lo envuelve: el diálogo necesita
 * saberlo para no cerrarse con un pedido viajando.
 */
interface BookingFormProps {
  station: StationResult
  connector: ConnectorSummary
  flow: BookingFlow
  /** Para que el diálogo se nombre con el título de acá. */
  titleId: string
  /**
   * Salir sin reservar. En el diálogo lo cierra; en el celular vuelve al detalle de la estación,
   * y por eso el texto del botón lo decide quien lo envuelve.
   */
  onCancel: () => void
  cancelLabel: string
  /** Terminar después de confirmar. Recibe la reserva recién guardada. */
  onDone: (booking: Booking) => void
}

/** La duración que arranca elegida: la más común para una carga rápida. */
const DEFAULT_DURATION = 60

/**
 * Lo elegido en el primer paso.
 *
 * Vive en `BookingForm` y no adentro del paso porque el paso se desmonta al pasar a la revisión:
 * guardado ahí, "Cambiar horario" volvería con todo en blanco en vez de con lo que el conductor
 * ya había elegido y solo quiere retocar. `null` es "todavía nada", y cae en el valor por omisión.
 */
interface Choice {
  day: Date | null
  start: Date | null
  duration: number
}

const GHOST_BUTTON =
  'text-text-muted hover:text-text focus-visible:outline-primary cursor-pointer rounded-xl px-4 py-2.5 text-sm font-semibold transition-colors focus-visible:outline-2 disabled:cursor-not-allowed disabled:opacity-50'

const PRIMARY_BUTTON =
  'brand-fill text-on-primary focus-visible:outline-primary cursor-pointer rounded-xl px-5 py-2.5 text-sm font-bold focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-wait disabled:opacity-70'

/** Una opción elegible —un día, una duración—, con el mismo lenguaje que las filas de conector. */
function optionClass(selected: boolean): string {
  return `focus-visible:outline-primary cursor-pointer rounded-xl border transition-colors focus-visible:outline-2 disabled:cursor-not-allowed disabled:opacity-50 ${
    selected
      ? 'border-primary bg-surface text-text'
      : 'border-border bg-surface/40 text-text-muted hover:bg-surface/70 hover:text-text'
  }`
}

export default function BookingForm({
  station,
  connector,
  flow,
  titleId,
  onCancel,
  cancelLabel,
  onDone,
}: BookingFormProps) {
  const { step } = flow
  const payment = usePaymentReadiness()
  const [choice, setChoice] = useState<Choice>({
    day: null,
    start: null,
    duration: DEFAULT_DURATION,
  })

  return (
    <div className="flex flex-col gap-5">
      <header className="flex flex-col gap-1">
        <p className="text-primary text-xs font-bold tracking-wide uppercase">
          {step.kind === 'confirmed' ? 'Reserva confirmada' : 'Reservar conector'}
        </p>
        <h2 id={titleId} className="text-text text-xl font-extrabold tracking-tight">
          {station.name}
        </h2>
        <p className="text-text-muted text-sm">
          {CONNECTOR_TYPE_LABEL[connector.connectorType]} · {formatPower(connector.maxPowerKw)} ·{' '}
          {station.address}
        </p>
      </header>

      {(step.kind === 'choosing' || step.kind === 'holding') && payment === 'checking' && (
        <p className="text-text-muted text-sm">Revisando tus medios de pago…</p>
      )}

      {(step.kind === 'choosing' || step.kind === 'holding') && payment === 'missing' && (
        <NoCardNotice onCancel={onCancel} cancelLabel={cancelLabel} />
      )}

      {(step.kind === 'choosing' || step.kind === 'holding') && payment === 'ready' && (
        <ChooseStep
          stationId={station.stationId}
          connectorId={connector.connectorId}
          choice={choice}
          onChoiceChange={setChoice}
          busy={step.kind === 'holding'}
          error={flow.error}
          onSubmit={flow.requestHold}
          onCancel={onCancel}
          cancelLabel={cancelLabel}
        />
      )}

      {(step.kind === 'reviewing' || step.kind === 'confirming') && (
        <ReviewStep
          key={step.hold.id}
          hold={step.hold}
          busy={step.kind === 'confirming'}
          error={flow.error}
          onConfirm={flow.confirm}
          onChangeWindow={flow.changeWindow}
          onExpire={flow.expire}
        />
      )}

      {step.kind === 'confirmed' && (
        <ConfirmedStep booking={step.booking} onDone={() => onDone(step.booking)} />
      )}
    </div>
  )
}

/**
 * El problema del último intento, con el foco puesto encima.
 *
 * **El foco no es decorativo.** El botón que se tocó queda deshabilitado mientras viaja el pedido,
 * y un elemento deshabilitado suelta el foco: cuando vuelve el error, el foco quedó en el fondo de
 * la página, detrás de la ventana. Llevarlo al mensaje lo deja adentro, donde sigue quien navega
 * con teclado, y el lector de pantalla lo lee igual por el `role="alert"`.
 */
function ErrorMessage({ error }: { error: BookingFlowError }) {
  const ref = useRef<HTMLDivElement>(null)
  const { message, addCard } = error

  useEffect(() => {
    ref.current?.focus()
  }, [message])

  return (
    <div ref={ref} role="alert" tabIndex={-1} className="flex flex-col gap-1 outline-none">
      <p className="text-danger text-sm">{message}</p>
      {addCard && (
        <Link
          to={ADD_CARD_PATH}
          className="text-primary focus-visible:outline-primary self-start rounded text-sm font-semibold underline-offset-2 hover:underline focus-visible:outline-2"
        >
          Agregar tarjeta
        </Link>
      )}
    </div>
  )
}

/** Donde se cargan las tarjetas: la sección de medios de pago del perfil. */
const ADD_CARD_PATH = '/profile/payment-methods'

/**
 * En lugar del horario, cuando el conductor no tiene tarjeta.
 *
 * Sin tarjeta el checkout rechaza la confirmación (RF02), así que dejarlo elegir horario sería
 * retenerle un conector diez minutos —y quitárselo a otro— para terminar en un error. Ver
 * `usePaymentReadiness`.
 */
function NoCardNotice({ onCancel, cancelLabel }: { onCancel: () => void; cancelLabel: string }) {
  return (
    <div className="flex flex-col gap-5">
      <div className="border-border bg-surface/40 flex flex-col gap-1 rounded-2xl border px-4 py-3 text-sm">
        <p className="text-text font-semibold">Para reservar necesitás una tarjeta registrada</p>
        <p className="text-text-muted">
          La tarjeta es obligatoria para reservar. Cargala desde tu perfil y volvé a este conector.
        </p>
      </div>

      <div className="flex flex-wrap items-center justify-end gap-2">
        <button type="button" onClick={onCancel} className={GHOST_BUTTON}>
          {cancelLabel}
        </button>
        <Link to={ADD_CARD_PATH} className={PRIMARY_BUTTON}>
          Agregar tarjeta
        </Link>
      </div>
    </div>
  )
}

/*
 * ---------------------------------------------------------------------------
 * Paso 1: el horario
 * ---------------------------------------------------------------------------
 */

interface ChooseStepProps {
  stationId: number
  connectorId: number
  choice: Choice
  onChoiceChange: (choice: Choice) => void
  busy: boolean
  error: BookingFlowError | null
  onSubmit: (start: Date, end: Date) => void
  onCancel: () => void
  cancelLabel: string
}

function ChooseStep({
  stationId,
  connectorId,
  choice,
  onChoiceChange,
  busy,
  error,
  onSubmit,
  onCancel,
  cancelLabel,
}: ChooseStepProps) {
  /*
   * El minuto alcanza: lo único que el reloj cambia acá es qué turnos de hoy quedan, y esos van
   * de a cuarto de hora.
   */
  const now = useNow(60_000)
  const days = bookableDays(now)

  const { start, duration } = choice

  /*
   * Los huecos libres del conector (ECO-33). Se vuelven a pedir cada vez que termina un intento de
   * retener —`busy` pasa a `false`—, porque si el backend acaba de contestar "tomado", la foto que
   * se estaba mostrando quedó vieja. Sin datos (`free` en `null`) se elige a ciegas, como antes.
   */
  const availability = useAvailability(connectorId, busy)
  const free = availability.status === 'ready' ? availability.free : null
  const ownHold = pendingHoldFor(connectorId, now)
  const shortest = DURATIONS_MINUTES[0]

  /*
   * El día elegido, o el primero que tenga lugar. Si el elegido se llenó —o hoy se quedó sin
   * turnos—, se pasa al siguiente con lugar en vez de dejar al conductor frente a una lista de
   * horas todas apagadas.
   */
  const chosenDay = choice.day
  const withRoom = days.filter((d) => dayHasRoom(free, d, now, shortest, ownHold))
  const selectedDay =
    (chosenDay === null ? undefined : withRoom.find((d) => isSameDay(d, chosenDay))) ??
    withRoom[0] ??
    days[0]
  const times = startTimesFor(selectedDay, now)

  /*
   * El inicio elegido se mantiene al cambiar de día si ese horario existe y está libre en el
   * nuevo: quien eligió las 18:00 del jueves y pasa al viernes, casi siempre quiere las 18:00 del
   * viernes. Si no, cae en el primero libre.
   */
  const available = times.filter((t) => startIsAvailable(free, t, shortest, ownHold))
  const sameTime =
    start === null
      ? undefined
      : available.find(
          (t) => t.getHours() === start.getHours() && t.getMinutes() === start.getMinutes(),
        )
  const selectedStart = sameTime ?? available[0] ?? null

  /*
   * La duración elegida, o la más larga que todavía entre desde ese inicio. Pasar de las 18:00 a
   * las 19:30 con cuatro horas elegidas y una reserva a las 21:00 no puede dejar el formulario
   * inválido sin avisar: se achica sola, y los botones que no entran quedan apagados.
   */
  const effectiveDuration =
    selectedStart === null || fits(free, selectedStart, duration, ownHold)
      ? duration
      : ([...DURATIONS_MINUTES].reverse().find((m) => fits(free, selectedStart, m, ownHold)) ??
        duration)

  const end = selectedStart === null ? null : windowEnd(selectedStart, effectiveDuration)
  const blocked = availability.status === 'unbookable'
  const dayStrip = useWheelToHorizontal<HTMLDivElement>()
  useDragToScroll(dayStrip)

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    if (selectedStart === null || end === null) return
    onSubmit(selectedStart, end)
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5">
      {/*
        Desde dónde sale, ARRIBA del día. No es un adorno del formulario: es lo que decide el
        recorrido que el mapa dibuja hasta esta estación y los minutos que el panel dice que hay
        hasta acá. Por omisión es la ubicación del dispositivo y no hay nada que tocar; está acá
        para los casos en que esa ubicación no sirve —sin permiso, con kilómetros de error, o
        reservando desde la oficina para salir de casa—.

        No forma parte de `Choice` y no viaja con la reserva: el punto de partida es del navegador
        y lo guarda su propio módulo (ver `departure`), porque lo mira el mapa de atrás y no solo
        este formulario. Lo que se reserva sigue siendo un conector por una ventana de tiempo.
      */}
      <DepartureField stationId={stationId} disabled={busy} />

      <fieldset className="flex min-w-0 flex-col gap-2" disabled={busy}>
        <legend className="text-text mb-2 text-sm font-semibold">Día</legend>
        {/*
          Los treinta días en una tira que scrollea de costado y no un calendario: la reserva es a
          lo sumo a un mes, y casi siempre para hoy o mañana, que quedan a la vista sin tocar nada.
          El `-mx`/`px` deja que la tira llegue hasta el borde del recuadro sin cortar la sombra
          del día elegido.

          En la computadora se mueve con la rueda y arrastrando con el mouse, además del dedo:
          la barra está oculta, y sin esas dos cosas el resto del mes queda fuera de alcance.
        */}
        <div
          ref={dayStrip}
          className="no-scrollbar -mx-1 flex cursor-grab gap-2 overflow-x-auto px-1 pb-1"
        >
          {days.map((d) => {
            const selected = isSameDay(d, selectedDay)
            /* Un día sin ningún hueco queda apagado y tachado: se ve que existe, pero que está lleno. */
            const full = !withRoom.some((room) => isSameDay(room, d))
            return (
              <button
                key={d.getTime()}
                type="button"
                aria-pressed={selected}
                disabled={full}
                title={full ? 'Sin horarios libres' : undefined}
                /*
                  Se fija la hora que se está viendo aunque el conductor no la haya tocado. Si no,
                  al pasar de hoy a mañana el horario por omisión —el primero de hoy, las 16:30—
                  se cambiaría por el primero de mañana, las 00:00.
                */
                onClick={() =>
                  onChoiceChange({ ...choice, day: d, start: choice.start ?? selectedStart })
                }
                className={`flex w-16 shrink-0 flex-col items-center gap-0.5 py-2 ${optionClass(selected)}`}
              >
                <span className="text-xs font-medium">{dayName(d, now)}</span>
                <span className={`text-lg leading-none font-bold ${full ? 'line-through' : ''}`}>
                  {dayNumber(d)}
                </span>
              </button>
            )
          })}
        </div>
      </fieldset>

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-[auto_1fr]">
        <div className="flex flex-col gap-2">
          <label htmlFor="booking-start" className="text-text text-sm font-semibold">
            Hora de inicio
          </label>
          {/*
            Un <select> nativo y no un selector de hora propio: en el celular abre la rueda del
            sistema, que es lo que la mano ya sabe usar. Las horas ocupadas siguen en la lista
            pero apagadas y con la aclaración: si desaparecieran, las 18:00 faltando entre las
            17:45 y las 18:15 se leería como un error de la lista.
          */}
          <select
            id="booking-start"
            disabled={busy || blocked || selectedStart === null}
            value={selectedStart?.getTime() ?? ''}
            onChange={(event) =>
              onChoiceChange({ ...choice, start: new Date(Number(event.target.value)) })
            }
            className="border-border bg-surface/60 text-text focus:border-primary min-w-32 cursor-pointer rounded-xl border px-3 py-2.5 text-sm tabular-nums outline-none disabled:cursor-not-allowed disabled:opacity-50"
          >
            {times.map((t) => {
              const available = startIsAvailable(free, t, shortest, ownHold)
              return (
                <option key={t.getTime()} value={t.getTime()} disabled={!available}>
                  {available ? formatTime(t) : `${formatTime(t)} · ocupado`}
                </option>
              )
            })}
          </select>
        </div>

        <fieldset className="flex min-w-0 flex-col gap-2" disabled={busy}>
          <legend className="text-text mb-2 text-sm font-semibold">Duración</legend>
          <div className="grid grid-cols-4 gap-2">
            {DURATIONS_MINUTES.map((minutes) => {
              const fitsHere = selectedStart === null || fits(free, selectedStart, minutes, ownHold)
              return (
                <button
                  key={minutes}
                  type="button"
                  aria-pressed={effectiveDuration === minutes}
                  disabled={!fitsHere}
                  title={fitsHere ? undefined : 'No entra antes del próximo horario ocupado'}
                  onClick={() => onChoiceChange({ ...choice, duration: minutes })}
                  className={`py-2.5 text-sm font-semibold ${optionClass(effectiveDuration === minutes)}`}
                >
                  {formatDuration(minutes)}
                </button>
              )
            })}
          </div>
        </fieldset>
      </div>

      {/*
        La ventana escrita entera antes de mandarla. Con tres controles separados es fácil elegir
        el día equivocado y no notarlo; leída de corrido, "jueves 17" salta a la vista.
      */}
      {selectedStart !== null && end !== null && (
        <p className="border-border bg-surface/40 text-text rounded-2xl border px-4 py-3 text-sm">
          <span className="text-text-muted">Tu horario: </span>
          <span className="font-semibold">{formatWindow(selectedStart, end)}</span>
        </p>
      )}

      {/*
        Mientras llegan los huecos se puede elegir igual: el aviso dice que las horas ocupadas
        todavía no están marcadas, en vez de bloquear el formulario por una consulta.
      */}
      {availability.status === 'loading' && (
        <p className="text-text-muted text-xs">Consultando horarios libres…</p>
      )}
      {availability.status === 'unknown' && (
        <p className="text-text-muted text-xs">
          No pudimos consultar los horarios libres. Podés elegir uno igual: si está tomado, te
          avisamos.
        </p>
      )}
      {availability.status === 'ready' && selectedStart === null && (
        <p className="text-text-muted text-sm">No quedan horarios libres en este conector.</p>
      )}
      {blocked && <ErrorMessage error={{ message: availability.message, addCard: false }} />}

      {error !== null && <ErrorMessage error={error} />}

      <div className="flex flex-wrap items-center justify-end gap-2">
        <button type="button" onClick={onCancel} disabled={busy} className={GHOST_BUTTON}>
          {cancelLabel}
        </button>
        <button
          type="submit"
          disabled={busy || blocked || selectedStart === null}
          className={PRIMARY_BUTTON}
        >
          {busy ? 'Reservando horario…' : 'Continuar'}
        </button>
      </div>
    </form>
  )
}

/*
 * ---------------------------------------------------------------------------
 * Paso 2: revisar y confirmar
 * ---------------------------------------------------------------------------
 */

interface ReviewStepProps {
  hold: Hold
  busy: boolean
  error: BookingFlowError | null
  onConfirm: (acceptGracePeriod: boolean) => void
  onChangeWindow: () => void
  onExpire: () => void
}

function ReviewStep({ hold, busy, error, onConfirm, onChangeWindow, onExpire }: ReviewStepProps) {
  const now = useNow(1000)
  const remaining = hold.expiresAt.getTime() - now.getTime()

  /*
   * Vencida sin confirmar, se vuelve a elegir el horario. No mientras viaja la confirmación:
   * si llega justo, que conteste el backend; si no llega, él dice que venció y el flujo vuelve
   * atrás igual.
   */
  const expired = remaining <= 0
  useEffect(() => {
    if (expired && !busy) onExpire()
  }, [expired, busy, onExpire])

  /* El último minuto se pinta de alerta: es cuando todavía hay tiempo de hacer algo. */
  const urgent = remaining <= 60_000

  /*
   * La aceptación de la tolerancia. Arranca sin tildar en cada retención: la hora límite es la de
   * ESTE horario, y un "acepto" dado para otro no vale para este. Por eso el paso se monta con
   * `key` en la retención, y "Cambiar horario" vuelve a pedirla.
   *
   * Viaja en la confirmación y el checkout la exige: sin ella contesta 400. Lo que todavía no hace
   * el backend es registrarla.
   */
  const [accepted, setAccepted] = useState(false)
  const deadline = formatTimeAfter(hold.start, graceDeadline(hold.start))

  return (
    <div className="flex flex-col gap-5">
      <p
        className={`flex items-center gap-2 rounded-2xl border px-4 py-3 text-sm ${
          urgent ? 'border-danger/50 text-danger' : 'border-primary/40 text-text'
        }`}
      >
        <span aria-hidden="true" className="relative flex h-2.5 w-2.5 shrink-0">
          <span
            className={`absolute inline-flex h-full w-full animate-ping rounded-full opacity-60 motion-reduce:animate-none ${
              urgent ? 'bg-danger' : 'bg-primary'
            }`}
          />
          <span
            className={`relative inline-flex h-2.5 w-2.5 rounded-full ${urgent ? 'bg-danger' : 'bg-primary'}`}
          />
        </span>
        <span>
          Te guardamos este horario por{' '}
          {/*
            Sin `aria-live`: un lector de pantalla anunciaría cada segundo. El tiempo está escrito
            y se lee cuando se lo busca.
          */}
          <strong className="font-bold tabular-nums">{formatCountdown(remaining)}</strong>. Confirmá
          antes de que venza.
        </span>
      </p>

      <dl className="border-border bg-surface/40 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 rounded-2xl border px-4 py-3 text-sm">
        <dt className="text-text-muted">Horario</dt>
        <dd className="text-text font-semibold first-letter:uppercase">
          {formatWindow(hold.start, hold.end)}
        </dd>
        <dt className="text-text-muted">Seña</dt>
        <dd className="text-text">Sin cobro por ahora</dd>
      </dl>

      {/*
        Todo el texto es la etiqueta: tocar cualquier parte tilda el check, que en el celular es la
        diferencia entre acertarle a un cuadradito de 16 px o no.
      */}
      <label className="text-text flex cursor-pointer items-start gap-3 text-sm leading-relaxed">
        <input
          type="checkbox"
          checked={accepted}
          onChange={(event) => setAccepted(event.target.checked)}
          disabled={busy}
          className="accent-primary mt-1 h-4 w-4 shrink-0 cursor-pointer disabled:cursor-not-allowed"
        />
        <span>
          Acepto que la reserva cuenta con una tolerancia de {GRACE_MINUTES} minutos desde su inicio
          (hasta las {deadline}). Transcurrido ese plazo sin que me presente, la reserva se
          cancelará automáticamente.
        </span>
      </label>

      {error !== null && <ErrorMessage error={error} />}

      <div className="flex flex-wrap items-center justify-end gap-2">
        <button type="button" onClick={onChangeWindow} disabled={busy} className={GHOST_BUTTON}>
          Cambiar horario
        </button>
        {/*
          Apagado hasta aceptar la tolerancia. El `disabled:cursor-wait` del botón es para cuando
          viaja la confirmación; sin aceptar, el cursor tiene que decir "no se puede" y no
          "esperá". Se reemplaza la clase en vez de sumar otra porque con dos `disabled:cursor-*`
          gana la que Tailwind escriba última en el CSS, no la última del atributo.
        */}
        <button
          type="button"
          onClick={() => onConfirm(accepted)}
          disabled={busy || expired || !accepted}
          className={
            accepted
              ? PRIMARY_BUTTON
              : PRIMARY_BUTTON.replace('disabled:cursor-wait', 'disabled:cursor-not-allowed')
          }
        >
          {busy ? 'Confirmando…' : 'Confirmar reserva'}
        </button>
      </div>
    </div>
  )
}

/*
 * ---------------------------------------------------------------------------
 * Paso 3: confirmada
 * ---------------------------------------------------------------------------
 */

function ConfirmedStep({ booking, onDone }: { booking: Booking; onDone: () => void }) {
  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-start gap-3">
        <span
          aria-hidden="true"
          className="brand-fill text-on-primary flex h-10 w-10 shrink-0 items-center justify-center rounded-full"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="3"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="h-5 w-5"
          >
            <path d="m5 12 5 5 9-10" />
          </svg>
        </span>
        <div className="flex flex-col gap-1">
          <p className="text-text text-sm font-semibold first-letter:uppercase">
            {formatWindow(booking.start, booking.end)}
          </p>
          <p className="text-text-muted text-sm">
            El conector queda bloqueado para vos en ese horario. La vas a ver en tu perfil, en
            Reservas e historial.
          </p>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-end gap-2">
        <Link to="/profile/reservations" className={GHOST_BUTTON}>
          Ver mis reservas
        </Link>
        <button type="button" onClick={onDone} autoFocus className={PRIMARY_BUTTON}>
          Listo
        </button>
      </div>
    </div>
  )
}
