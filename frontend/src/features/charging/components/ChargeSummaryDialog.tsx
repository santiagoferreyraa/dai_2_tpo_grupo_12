import { useEffect, useId, useRef } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router'

import type { ChargingSession } from '../types'
import SessionSummary from './SessionSummary'

interface ChargeSummaryDialogProps {
  session: ChargingSession
  onClose: () => void
}

/**
 * El resumen de la carga terminada, encima de la escena de la carga.
 *
 * Mismo marco que reservar desde el mapa (`BookingDialog`): portal en `<body>` y fondo negro al 50%
 * con desenfoque. Lo que cambia es lo de adentro: en vez del vidrio modal va el ticket, que entra
 * desde abajo como saliendo de una impresora.
 *
 * Va en un portal por lo mismo que aquél: el recuadro de la carga es de vidrio, y un ancestro con
 * `backdrop-filter` haría que el oscurecido cubriera solo el recuadro.
 *
 * **La escena queda visible detrás a propósito**: el fluido quieto en el nivel al que llegó la
 * batería es la otra mitad del resumen.
 */
export default function ChargeSummaryDialog({ session, onClose }: ChargeSummaryDialogProps) {
  const titleId = useId()
  const dialogRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  useEffect(() => {
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previous
    }
  }, [])

  useEffect(() => {
    dialogRef.current?.focus()
  }, [])

  return createPortal(
    <div
      className="fixed inset-0 z-[1200] flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm md:p-6"
      onClick={onClose}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
        className="charge-ticket-enter flex max-h-full w-full max-w-sm flex-col gap-4 overflow-y-auto outline-none"
      >
        <SessionSummary
          session={session}
          titleId={titleId}
          corner={
            <button
              type="button"
              onClick={onClose}
              aria-label="Cerrar"
              className="text-text-muted hover:text-text hover:bg-border/40 focus-visible:outline-primary absolute top-4 right-4 flex h-9 w-9 cursor-pointer items-center justify-center rounded-full transition-colors focus-visible:outline-2"
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
          }
        />

        {/* Fuera del ticket: el ticket es el comprobante, y el botón es lo que se hace después. */}
        <Link
          to="/"
          className="brand-fill text-on-primary rounded-xl px-5 py-3 text-center text-sm font-bold shadow-lg"
        >
          Volver al inicio
        </Link>
      </div>
    </div>,
    document.body,
  )
}
