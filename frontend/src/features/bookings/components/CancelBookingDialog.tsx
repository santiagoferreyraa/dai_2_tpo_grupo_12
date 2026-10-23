import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

import { refreshNotificationsSoon } from '@/features/notifications/data/notificationsStore'
import { ApiError } from '@/lib/api'

import { cancelBooking } from '../data/bookingsRepository'
import { refreshMyBookings } from '../data/myBookingsStore'
import { formatWindow } from '../slots'
import type { Booking } from '../types'

/**
 * La confirmación antes de cancelar una reserva.
 *
 * **Pregunta antes porque cancelar no tiene vuelta atrás desde la app.** La ventana se libera en
 * el acto y cualquier otro conductor la puede tomar; volver a reservar el mismo horario puede ya
 * no ser posible. Un toque sin querer sobre "Cancelar" no puede costar eso.
 *
 * Mismo marco que la ventana de reservar y la de cerrar sesión: vidrio modal sobre el fondo
 * oscurecido. El foco arranca en "Mantener", que es la salida que no rompe nada.
 */
interface CancelBookingDialogProps {
  booking: Booking
  onClose: () => void
}

export default function CancelBookingDialog({ booking, onClose }: CancelBookingDialogProps) {
  const titleId = useId()
  const keepRef = useRef<HTMLButtonElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const close = useCallback(() => {
    if (!busy) onClose()
  }, [busy, onClose])

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [close])

  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    keepRef.current?.focus()
    return () => previous?.focus()
  }, [])

  function handleCancel() {
    setBusy(true)
    setError(null)

    cancelBooking(booking.id).then(
      () => {
        refreshMyBookings()
        /* Cancelar también genera un aviso. Ver `refreshNotificationsSoon`. */
        refreshNotificationsSoon()
        onClose()
      },
      (reason: unknown) => {
        setBusy(false)
        /*
          Un 400 es que la ventana terminó mientras se decidía: ya no hay nada que liberar, y la
          lista se refresca para que la reserva pase al historial. Un 404, que ya no existe.
        */
        if (reason instanceof ApiError && reason.status === 400) {
          refreshMyBookings()
          setError('Esta reserva ya terminó, así que no hay nada que cancelar.')
          return
        }
        if (reason instanceof ApiError && reason.status === 404) {
          refreshMyBookings()
          setError('Esta reserva ya no existe.')
          return
        }
        if (reason instanceof ApiError && reason.status === 0) {
          setError('No se pudo conectar con el servidor. Probá de nuevo.')
          return
        }
        setError('No se pudo cancelar la reserva. Probá de nuevo.')
      },
    )
  }

  const place = booking.location?.stationName ?? `el conector ${String(booking.connectorId)}`

  return createPortal(
    /*
      En un portal, directo en <body>, y no donde se lo monta. Las tarjetas de vidrio usan
      `backdrop-filter`, y un ancestro con filtro se vuelve la referencia de los elementos
      `fixed`: adentro de una, el oscurecido cubría solo la tarjeta y no la navegación de arriba.
    */
    <div
      className="fixed inset-0 z-[1200] flex items-center justify-center bg-black/50 p-6 backdrop-blur-sm"
      onClick={close}
    >
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(event) => event.stopPropagation()}
        className="glass-panel glass-modal w-full max-w-sm rounded-3xl p-6"
      >
        <h2 id={titleId} className="text-text text-lg font-extrabold tracking-tight">
          ¿Cancelás la reserva?
        </h2>
        <p className="text-text-muted mt-2 text-sm leading-relaxed">
          En {place},{' '}
          <span className="text-text font-semibold">
            {formatWindow(booking.start, booking.end)}
          </span>
          . El horario queda libre y cualquier otro conductor lo puede reservar.
        </p>

        {error !== null && (
          <p role="alert" className="text-danger mt-3 text-sm">
            {error}
          </p>
        )}

        <div className="mt-6 flex items-center justify-end gap-2">
          <button
            ref={keepRef}
            type="button"
            onClick={close}
            disabled={busy}
            className="text-text-muted hover:text-text cursor-pointer rounded-xl px-4 py-2.5 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50"
          >
            Mantener
          </button>
          <button
            type="button"
            onClick={handleCancel}
            disabled={busy}
            className="brand-fill text-on-primary cursor-pointer rounded-xl px-4 py-2.5 text-sm font-semibold disabled:cursor-wait disabled:opacity-70"
          >
            {busy ? 'Cancelando…' : 'Cancelar reserva'}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
