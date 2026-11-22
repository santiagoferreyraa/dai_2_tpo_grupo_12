import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

import { describeDebt, formatDebtAmount } from '../debts'
import type { Debt, PaymentMethod } from '../types'

import CardPicker from './CardPicker'

interface PayDebtDialogProps {
  /** La deuda a pagar; `null` con el diálogo cerrado. */
  debt: Debt | null
  cards: PaymentMethod[]
  /** Paga con la tarjeta elegida. El error, si lo hay, vuelve por `error`. */
  onPay: (debt: Debt, cardId: number) => void
  onCancel: () => void
  busy: boolean
  error: string | null
}

/**
 * El diálogo de "Pagar ahora" (ECO-62): el monto, con qué tarjeta y el botón de pagar.
 *
 * **La tarjeta se elige con la misma pila que al reservar** (`CardPicker`), y arranca en la más
 * reciente que sirva, que es la que el backend usaría por su cuenta. Es lo más probable que el
 * conductor quiera cambiar: si la deuda existe es porque una tarjeta rechazó, y lo habitual es
 * cargar otra y pagar con esa.
 *
 * **El error se muestra adentro y el diálogo no se cierra**, al revés que la baja de una tarjeta:
 * acá el siguiente paso natural es elegir otra tarjeta y volver a intentar, y para eso hay que
 * seguir viendo la pila.
 *
 * Se dibuja en `document.body` por lo mismo que `ConfirmDialog`: la sección del perfil es un
 * `.glass-panel` y su `backdrop-filter` encerraría el `position: fixed`.
 */
export default function PayDebtDialog({
  debt,
  cards,
  onPay,
  onCancel,
  busy,
  error,
}: PayDebtDialogProps) {
  const open = debt !== null
  const cancelRef = useRef<HTMLButtonElement>(null)
  const errorRef = useRef<HTMLParagraphElement>(null)

  /* Lo que el conductor tocó; `null` mientras no tocó nada. */
  const [pickedId, setPickedId] = useState<number | null>(null)

  /*
   * La tarjeta que se va a usar: la que tocó, si sigue sirviendo, o la más reciente que sirva (la
   * lista viene del backend de la más nueva a la más vieja). Se calcula en cada render y no se
   * copia a un estado al abrir: así una tarjeta dada de baja con el diálogo abierto no queda
   * elegida. `DebtNotice` monta un diálogo nuevo por deuda, así que lo tocado no pasa de una a otra.
   */
  const selectedId =
    cards.find((card) => card.id === pickedId && !card.expired)?.id ??
    cards.find((card) => !card.expired)?.id ??
    null

  useEffect(() => {
    if (open) cancelRef.current?.focus()
  }, [open])

  /*
   * El botón de pagar queda deshabilitado mientras viaja el pedido y suelta el foco: cuando vuelve
   * el error, se lo lleva al mensaje para que no quede en el fondo de la página.
   */
  useEffect(() => {
    if (error !== null) errorRef.current?.focus()
  }, [error])

  useEffect(() => {
    if (!open) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) onCancel()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [open, busy, onCancel])

  useEffect(() => {
    if (!open) return
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previous
    }
  }, [open])

  if (debt === null) return null

  const hasUsableCard = cards.some((card) => !card.expired)

  return createPortal(
    /* El mismo escalón que `ConfirmDialog`: por encima de la franja y del panel que sube. */
    <div className="fixed inset-0 z-[1210] flex items-center justify-center p-6">
      <button
        type="button"
        aria-label="Cancelar"
        onClick={busy ? undefined : onCancel}
        className="absolute inset-0 h-full w-full cursor-default bg-black/50"
      />

      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="pay-debt-title"
        className="border-border bg-background relative flex max-h-full w-full max-w-sm flex-col gap-5 overflow-y-auto rounded-2xl border p-6 shadow-2xl"
      >
        <div>
          <h2 id="pay-debt-title" className="text-lg font-semibold">
            Pagar la {describeDebt(debt).toLowerCase()}
          </h2>
          <p className="text-text-muted mt-1 text-sm">
            Vas a pagar{' '}
            <span className="text-text font-semibold">{formatDebtAmount(debt.amount)}</span>.
          </p>
        </div>

        {hasUsableCard ? (
          <div className="flex flex-col gap-3">
            <p className="text-text text-sm font-semibold">
              {cards.length > 1 ? 'Elegí con qué tarjeta pagar' : 'Pagás con'}
            </p>
            <CardPicker
              cards={cards}
              selectedId={selectedId}
              onSelect={(card) => setPickedId(card.id)}
              disabled={busy}
              label="Tarjeta para pagar la deuda"
            />
          </div>
        ) : (
          <p className="text-text-muted text-sm">
            Necesitás una tarjeta vigente para pagar. Cargala desde esta misma sección y volvé a
            intentar.
          </p>
        )}

        {error !== null && (
          <p ref={errorRef} role="alert" tabIndex={-1} className="text-danger text-sm outline-none">
            {error}
          </p>
        )}

        <div className="flex gap-3">
          <button
            ref={cancelRef}
            type="button"
            onClick={onCancel}
            disabled={busy}
            className="border-border hover:bg-surface flex-1 rounded-lg border px-4 py-2.5 text-sm font-medium disabled:opacity-60"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => {
              if (selectedId !== null) onPay(debt, selectedId)
            }}
            disabled={busy || selectedId === null}
            className="bg-primary hover:bg-primary-strong text-on-primary flex-1 rounded-lg px-4 py-2.5 text-sm font-semibold disabled:opacity-60"
          >
            {busy ? 'Pagando…' : 'Pagar'}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
