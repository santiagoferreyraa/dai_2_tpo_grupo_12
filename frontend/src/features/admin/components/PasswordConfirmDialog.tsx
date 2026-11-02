import { type FormEvent, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'

interface PasswordConfirmDialogProps {
  open: boolean
  title: string
  /** A quién y qué se le hace: nombre, mail y rol. */
  subject: string
  /** Qué pasa después, en una frase. */
  consequence: string
  confirmLabel: string
  busyLabel: string
  busy: boolean
  /** El error que se muestra pegado al campo, o `null`. El diálogo sigue abierto para corregir. */
  error: string | null
  onConfirm: (password: string) => void
  onCancel: () => void
}

/**
 * El "¿seguro?" de las bajas y los cambios de rol de `/admin`, con la contraseña del ADMIN
 * (decidido por Manuel el 02/11). El backend es quien la exige; esto es el lugar para escribirla.
 *
 * **Es aparte de `ConfirmDialog` y no una opción suya** porque ese no tiene formulario: acá hay un
 * campo, un error que se muestra adentro sin cerrar el cartel y Enter que confirma. Toma de él el
 * portal y el escalón de z-index, por las mismas razones (ver su comentario).
 *
 * **El foco va al campo al abrir**, no a Cancelar: lo único que se puede hacer acá es escribir, y
 * sin la contraseña el botón de confirmar no hace nada.
 *
 * **Se monta solo mientras está abierto** (lo hace `AdminPage`), así que cada apertura empieza con
 * el campo vacío sin tener que limpiarlo a mano.
 */
export default function PasswordConfirmDialog({
  open,
  title,
  subject,
  consequence,
  confirmLabel,
  busyLabel,
  busy,
  error,
  onConfirm,
  onCancel,
}: PasswordConfirmDialogProps) {
  const [password, setPassword] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  // Al abrir, y después de cada error, el foco vuelve al campo, listo para escribir.
  useEffect(() => {
    if (open) inputRef.current?.focus()
  }, [open, error])

  useEffect(() => {
    if (!open) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !busy) onCancel()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [open, busy, onCancel])

  if (!open) return null

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (password === '' || busy) return
    onConfirm(password)
    // Se borra al enviar: si vuelve con error, el campo ya está vacío para reescribirla.
    setPassword('')
  }

  return createPortal(
    <div className="fixed inset-0 z-[1210] flex items-center justify-center p-6">
      <button
        type="button"
        aria-label="Cancelar"
        onClick={() => !busy && onCancel()}
        className="absolute inset-0 h-full w-full cursor-default bg-black/50"
      />

      <form
        role="dialog"
        aria-modal="true"
        aria-labelledby="password-confirm-title"
        onSubmit={submit}
        className="border-border bg-background relative w-full max-w-sm rounded-2xl border p-6 shadow-2xl"
      >
        <h2 id="password-confirm-title" className="text-lg font-semibold">
          {title}
        </h2>
        <p className="mt-2 text-sm font-semibold break-words">{subject}</p>
        <p className="text-text-muted mt-2 text-sm">{consequence}</p>

        <label htmlFor="password-confirm-input" className="mt-5 block text-sm font-medium">
          Tu contraseña
        </label>
        <input
          id="password-confirm-input"
          ref={inputRef}
          type="password"
          autoComplete="current-password"
          value={password}
          disabled={busy}
          onChange={(event) => setPassword(event.target.value)}
          aria-invalid={error !== null}
          aria-describedby={error !== null ? 'password-confirm-error' : undefined}
          className="border-border bg-surface/90 focus:border-primary mt-1.5 w-full rounded-lg border px-3 py-2 text-sm focus:outline-none disabled:opacity-60"
        />
        {error !== null && (
          <p id="password-confirm-error" role="alert" className="mt-2 text-sm text-rose-400">
            {error}
          </p>
        )}

        <div className="mt-6 flex gap-3">
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            className="border-border hover:bg-surface flex-1 rounded-lg border px-4 py-2.5 text-sm font-medium disabled:opacity-60"
          >
            Cancelar
          </button>
          <button
            type="submit"
            disabled={busy || password === ''}
            className="bg-primary hover:bg-primary-strong flex-1 rounded-lg px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60"
          >
            {busy ? busyLabel : confirmLabel}
          </button>
        </div>
      </form>
    </div>,
    document.body,
  )
}
