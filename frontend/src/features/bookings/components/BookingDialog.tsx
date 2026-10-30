import { useCallback, useEffect, useId, useRef } from 'react'
import { createPortal } from 'react-dom'

import type { ConnectorSummary, StationResult } from '@/features/terminals/types'

import type { Booking } from '../types'
import { useBookingFlow } from '../useBookingFlow'
import BookingForm from './BookingForm'

/**
 * Reservar un conector en PC y tablet: una ventana centrada sobre el fondo oscurecido.
 *
 * **Oscurece todo lo de atrás a propósito.** Mientras se elige el horario lo único que importa es
 * este recuadro, y el mapa de atrás —con sus pines, su carrusel y su panel— compite por la mirada
 * y por el clic. En el celular no hay diálogo: el mismo contenido va adentro del panel que sube
 * desde abajo. Ver `BookingPanel`.
 *
 * Mismo marco que la ventana de cerrar sesión (`ProfileExit`): vidrio modal, fondo negro al 50%
 * con desenfoque, y z-index por encima de los controles de Leaflet y de la barra del celular.
 */
interface BookingDialogProps {
  station: StationResult
  connector: ConnectorSummary
  /** Se llama al cerrar. Con la reserva si se confirmó, con `null` si se salió sin reservar. */
  onClose: (booking: Booking | null) => void
}

export default function BookingDialog({ station, connector, onClose }: BookingDialogProps) {
  const flow = useBookingFlow(connector.connectorId)
  const titleId = useId()
  const dialogRef = useRef<HTMLDivElement>(null)

  /*
   * Con un pedido viajando no se cierra, ni con Escape ni tocando afuera. Cerrar no lo cancela: la
   * retención o la reserva se crean igual en el backend, y el conductor se quedaría sin saber si
   * reservó o no.
   */
  const busy = flow.step.kind === 'holding' || flow.step.kind === 'confirming'
  const confirmed = flow.step.kind === 'confirmed' ? flow.step.booking : null

  /*
   * Cerrar desde el paso final cuenta como terminar: si la reserva ya se guardó, salir con Escape
   * o tocando afuera no la deshace, así que quien abrió el diálogo tiene que enterarse igual.
   */
  const close = useCallback(() => {
    if (busy) return
    onClose(confirmed)
  }, [busy, confirmed, onClose])

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [close])

  /* Con la ventana abierta el fondo no scrollea: si no, se mueve lo de atrás mientras se decide. */
  useEffect(() => {
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previous
    }
  }, [])

  /*
   * El foco entra a la ventana al abrir y vuelve a donde estaba al cerrar —el botón Reservar—.
   * Sin lo primero, quien navega con teclado sigue tabulando por el mapa de atrás; sin lo segundo,
   * al cerrar el foco cae al principio de la página.
   */
  useEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    dialogRef.current?.focus()
    return () => previous?.focus()
  }, [])

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
      {/* El clic de adentro no se propaga: sin esto, tocar el propio contenido cerraría la ventana. */}
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
        /*
          Más ancha cuando la revisión muestra las tarjetas: van en una columna propia, a la
          derecha. La revisión lo avisa con `data-booking-wide`, así el diálogo no tiene que saber
          qué tarjetas hay. Ver `ReviewStep`.
        */
        className="glass-panel glass-modal relative max-h-full w-full max-w-lg overflow-y-auto rounded-3xl p-6 outline-none has-[[data-booking-wide]]:max-w-4xl"
      >
        <button
          type="button"
          onClick={close}
          disabled={busy}
          aria-label="Cerrar"
          className="text-text-muted hover:text-text hover:bg-surface/70 focus-visible:outline-primary absolute top-4 right-4 flex h-9 w-9 cursor-pointer items-center justify-center rounded-full transition-colors focus-visible:outline-2 disabled:cursor-not-allowed disabled:opacity-40"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            aria-hidden="true"
            className="h-5 w-5"
          >
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
        </button>

        <BookingForm
          station={station}
          connector={connector}
          flow={flow}
          titleId={titleId}
          onCancel={close}
          cancelLabel="Cancelar"
          onDone={(booking) => onClose(booking)}
        />
      </div>
    </div>,
    document.body,
  )
}
